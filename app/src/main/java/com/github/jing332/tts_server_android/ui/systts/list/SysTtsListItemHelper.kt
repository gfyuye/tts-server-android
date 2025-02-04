package com.github.jing332.tts_server_android.ui.systts.list

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.View.AccessibilityDelegate
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.drake.brv.BindingAdapter
import com.drake.net.utils.withIO
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.constant.SpeechTarget
import com.github.jing332.tts_server_android.data.appDb
import com.github.jing332.tts_server_android.data.entities.systts.SystemTts
import com.github.jing332.tts_server_android.databinding.SysttsListItemBinding
import com.github.jing332.tts_server_android.help.audio.AudioPlayer
import com.github.jing332.tts_server_android.help.config.AppConfig
import com.github.jing332.tts_server_android.help.config.SysTtsConfig
import com.github.jing332.tts_server_android.model.tts.ITextToSpeechEngine
import com.github.jing332.tts_server_android.service.systts.SystemTtsService
import com.github.jing332.tts_server_android.ui.systts.base.QuickEditBottomSheet
import com.github.jing332.tts_server_android.ui.systts.edit.BaseParamsEditView
import com.github.jing332.tts_server_android.ui.systts.edit.BaseTtsEditActivity
import com.github.jing332.tts_server_android.ui.view.AppDialogs
import com.github.jing332.tts_server_android.ui.view.AppDialogs.displayErrorDialog
import com.github.jing332.tts_server_android.ui.view.widget.WaitDialog
import com.github.jing332.tts_server_android.util.clickWithThrottle
import com.github.jing332.tts_server_android.util.clone
import com.github.jing332.tts_server_android.util.longToast
import com.github.jing332.tts_server_android.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class SysTtsListItemHelper(val fragment: Fragment, val hasGroup: Boolean = false) {
    val context: Context by lazy { fragment.requireContext() }

    // 已经显示长按时的拖拽提示
    private var hasShownTip = false

    fun init(adapter: BindingAdapter, holder: BindingAdapter.BindingViewHolder) {
        adapter.apply {
            holder.apply {
                getBindingOrNull<SysttsListItemBinding>()?.apply {
                    checkBoxSwitch.setOnClickListener { view ->
                        if (hasGroup) {
                            getModelOrNull<GroupModel>(findParentPosition())?.let { group ->
                                // 组中的item位置
                                val subPos = modelPosition - findParentPosition() - 1
                                switchChanged(view, (group.itemSublist!![subPos] as ItemModel).data)
                            }
                        } else
                            switchChanged(view, getModel<ItemModel>().data)
                    }
                    if (hasGroup)
                        checkBoxSwitch.setOnLongClickListener {
                            itemTouchHelper?.startDrag(holder)
                            true
                        }

                    cardView.clickWithThrottle {
                        displayLiteEditDialog(
                            itemView,
                            getModel<ItemModel>().data
                        )
                    }
                    cardView.setOnLongClickListener {
                        if (hasGroup && !hasShownTip) {
                            hasShownTip = true
                            context.longToast(R.string.systts_drag_tip_msg)
                        }

                        val model = getModel<ItemModel>().data.copy()
                        if (model.speechTarget == SpeechTarget.BGM) return@setOnLongClickListener true

                        if (model.speechTarget == SpeechTarget.ASIDE)
                            model.speechTarget = SpeechTarget.DIALOGUE
                        else
                            model.speechTarget = SpeechTarget.ASIDE

                        appDb.systemTtsDao.updateTts(model)
                        notifyTtsUpdate(model.isEnabled)

                        true
                    }


                    btnListen.isVisible = AppConfig.isSwapListenAndEditButton
                    btnEdit.isGone = AppConfig.isSwapListenAndEditButton

                    btnListen.clickWithThrottle { listen(getModel<ItemModel>().data) }
                    btnEdit.clickWithThrottle { edit(getModel<ItemModel>().data) }
                    btnListen.setOnLongClickListener {
                        edit(getModel<ItemModel>().data)
                        true
                    }

                    btnMore.clickWithThrottle { displayMoreOptions(it, getModel<ItemModel>().data) }
                    btnMore.setOnLongClickListener {
                        if (AppConfig.isSwapListenAndEditButton) edit(getModel<ItemModel>().data)
                        else listen(getModel<ItemModel>().data)

                        true
                    }

                    itemView.accessibilityDelegate = object : AccessibilityDelegate() {
                        override fun onInitializeAccessibilityNodeInfo(
                            host: View,
                            info: AccessibilityNodeInfo
                        ) {
                            super.onInitializeAccessibilityNodeInfo(host, info)

                            val data = getSystemTTS(holder)
                            var str =
                                if (data.isEnabled) context.getString(R.string.enabled) else ""
                            if (data.speechRule.isStandby) str += ", " + context.getString(R.string.as_standby)
                            info.text = "$str, ${tvName.text}, " + context.getString(
                                R.string.systts_list_item_desc,
                                tvRaTarget.text,
                                tvApiType.text,
                                tvDescription.text,
                                tvBottomContent.text,
                            )
                        }
                    }

                }
            }
        }
    }

    fun getSystemTTS(holder: BindingAdapter.BindingViewHolder): SystemTts {
        return holder.getModelOrNull<SystemTts>() ?: holder.getModel<ItemModel>().data
    }

    private val audioPlayer by lazy { AudioPlayer(context) }
    private val waitDialog by lazy { WaitDialog(context) }

    private fun listen(model: SystemTts) {
        waitDialog.show()

        fragment.lifecycleScope.launch(Dispatchers.Main) {
            val tts = model.tts
            val audio = try {
                withIO {
                    tts.onLoad()
                    tts.getAudioWithSystemParams(AppConfig.testSampleText)
                }
            } catch (e: Exception) {
                context.displayErrorDialog(e)
                return@launch
            } finally {
                waitDialog.dismiss()
                model.tts.onDestroy()
            }

            if (audio == null) {
                context.displayErrorDialog(
                    Exception(
                        context.getString(
                            R.string.systts_log_audio_empty,
                            AppConfig.testSampleText
                        )
                    )
                )
                return@launch
            }

            val dlg = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.playing)
                .setMessage(AppConfig.testSampleText)
                .setPositiveButton(R.string.cancel, null)
                .setOnDismissListener {
                    audioPlayer.stop()
                    tts.onDestroy()
                }.show()

            withIO { audioPlayer.play(audio) }
            dlg.dismiss()
        }
    }

    private fun displayMoreOptions(v: View, model: SystemTts) {
        PopupMenu(context, v).apply {
            menuInflater.inflate(R.menu.sysstts_more_options, menu)
            setForceShowIcon(true)
            MenuCompat.setGroupDividerEnabled(menu, true)

            menu.findItem(R.id.menu_edit).isVisible = AppConfig.isSwapListenAndEditButton
            menu.findItem(R.id.menu_listen).isVisible = !AppConfig.isSwapListenAndEditButton

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_edit -> edit(model)
                    R.id.menu_listen -> listen(model)
                    R.id.menu_copy_config -> {
                        context.toast(R.string.systts_copied_config)
                        edit(model.copy(id = System.currentTimeMillis()))
                    }

                    R.id.menu_delete -> delete(model)
                }
                true
            }
            show()
        }

    }

    // 警告 多语音选项未开启
    private val checkMultiVoiceDialog by lazy {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.warning)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .setMessage(R.string.systts_please_check_multi_voice_option).create()
    }

    private fun switchChanged(view: View?, current: SystemTts) {
        val checkBox = view as CheckBox
        // 检测是否开启多语音
        if (setCheckBoxSwitch(appDb.systemTtsDao.allEnabledTts, current, checkBox.isChecked))
            notifyTtsUpdate()
        else {
            checkBox.isChecked = false
            checkMultiVoiceDialog.show()
        }
    }

    private fun setCheckBoxSwitch(
        list: List<SystemTts>,
        current: SystemTts,
        checked: Boolean
    ): Boolean {
        if (checked) {
            //检测多语音是否开启
            if (!SysTtsConfig.isMultiVoiceEnabled) {
                val target = current.speechTarget
                if (target == SpeechTarget.ASIDE || target == SpeechTarget.DIALOGUE)
                    return false
            }

            if (current.isStandby) {
                list.forEach {
                    if (it.speechRule.isStandby) {
                        if (it.speechRule.target == current.speechRule.target && it.speechRule.tag == current.speechRule.tag) {
                            appDb.systemTtsDao.updateTts(it.copy(isEnabled = false))
                        }
                    }
                }
            } else if (!SysTtsConfig.isVoiceMultipleEnabled) { // 多选关闭下 确保同目标只可单选
                list.forEach {
                    if (!it.isStandby && it.speechTarget == current.speechTarget && it.speechRule.tag == current.speechRule.tag)
                        appDb.systemTtsDao.updateTts(it.copy(isEnabled = false))
                }
            }
        }
        appDb.systemTtsDao.updateTts(current.copy(isEnabled = checked))

        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun displayLiteEditDialog(v: View, data: SystemTts) {
        // 修改数据要clone，不然对比时数据相同导致UI不更新
        data.clone<SystemTts>()?.let { clonedData ->
            val paramsEdit = clonedData.tts.getParamsEditView(context)
            val quickEdit = QuickEditBottomSheet(
                clonedData,
                paramsEdit.second,
                paramsEdit.first as BaseParamsEditView<*, ITextToSpeechEngine>
            ) {
                appDb.systemTtsDao.updateTts(clonedData)
                notifyTtsUpdate(clonedData.isEnabled)
                true
            }
            quickEdit.show(
                fragment.requireActivity().supportFragmentManager,
                QuickEditBottomSheet.TAG
            )
        }
    }

    // EditActivity的返回值
    private val startForResult =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val data = result.data?.getParcelableExtra<SystemTts>(BaseTtsEditActivity.KEY_DATA)
            data?.let {
                appDb.systemTtsDao.insertTts(data)
                notifyTtsUpdate(data.isEnabled)
            }
        }

    private fun notifyTtsUpdate(isUpdate: Boolean = true) {
        if (isUpdate) SystemTtsService.notifyUpdateConfig()
    }

    fun edit(data: SystemTts) {
        startForResult.launch(Intent(context, data.tts.getEditActivity()).apply {
            putExtra(BaseTtsEditActivity.KEY_DATA, data)
        })
    }

    fun delete(data: SystemTts) {
        AppDialogs.displayDeleteDialog(context, data.displayName.toString()) {
            appDb.systemTtsDao.deleteTts(data)
            notifyTtsUpdate(data.isEnabled)
        }
    }
}