package com.zfdang.touchhelper.ui.settings

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.zfdang.touchhelper.*

class ManagePackageWidgetsDialogFragment : DialogFragment() {
    companion object {
        private const val TAG = "DialogFragment"
    }

    private lateinit var editRules: EditText
    private var originalRules: String? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog!!.setCanceledOnTouchOutside(false)
        val view = inflater.inflate(R.layout.layout_manage_package_widgets, container, false)
        editRules = view.findViewById(R.id.editText_rules)
        originalRules = Settings.packageWidgetsInString
        editRules.setText(originalRules)
        val btReset = view.findViewById<Button>(R.id.button_reset)
        btReset?.setOnClickListener { editRules.setText(originalRules) }
        val btCopy = view.findViewById<Button>(R.id.button_copy)
        btCopy?.setOnClickListener { // Gets a handle to the clipboard service.
            val clipboard =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip =
                ClipData.newPlainText("Rules: Widget in Packages", editRules.text.toString())
            clipboard.setPrimaryClip(clip)
            Utilities.toast("规则已复制到剪贴板!")
        }
        val btPaste = view.findViewById<Button>(R.id.button_paste)
        btPaste?.setOnClickListener {
            val clipboard =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteData = clipData.getItemAt(0).text.toString()
                editRules.setText(pasteData)
                Utilities.toast("已从剪贴板获取规则!")
            } else {
                Utilities.toast("未从剪贴板发现规则!")
            }
        }
        val btCancel = view.findViewById<Button>(R.id.button_widgets_cancel)
        btCancel?.setOnClickListener {
            Utilities.toast("修改已取消")
            dialog!!.dismiss()
        }
        val btRules = view.findViewById<Button>(R.id.button_widgets_rules)
        btRules?.setOnClickListener {
            val url = "http://touchhelper.zfdang.com/rules"
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }
        val btConfirm = view.findViewById<Button>(R.id.button_widgets_confirm)
        btConfirm?.setOnClickListener {
            val result = Settings.setPackageWidgetsInString(editRules.text.toString())
            if (result) {
                Utilities.toast("规则已保存!")
                dialog!!.dismiss()
            } else {
                Utilities.toast("规则有误，请修改后再次保存!")
            }
        }
        return view
    }
}