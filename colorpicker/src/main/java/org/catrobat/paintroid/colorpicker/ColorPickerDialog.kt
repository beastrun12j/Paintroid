/*
 * Paintroid: An image manipulation application for Android.
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.paintroid.colorpicker

import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Shader.TileMode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FF = 0xff
private const val CURRENT_COLOR = "CurrentColor"
private const val INITIAL_COLOR = "InitialColor"
private const val REQUEST_CODE = 1
private const val BITMAP_NAME = "temp.png"
const val COLOR_EXTRA = "colorExtra"
const val BITMAP_NAME_EXTRA = "bitmapNameExtra"

class ColorPickerDialog : AppCompatDialogFragment(), OnColorChangedListener {
    @VisibleForTesting
    var onColorPickedListener = mutableListOf<OnColorPickedListener>()
    private lateinit var colorPickerView: ColorPickerView
    private lateinit var currentColorView: View
    private lateinit var newColorView: View
    private lateinit var pipetteBtn: MaterialButton
    private lateinit var checkeredShader: Shader
    private lateinit var currentBitmap: Bitmap

    private var colorToApply = 0

    companion object {
        fun newInstance(@ColorInt initialColor: Int): ColorPickerDialog {
            val dialog = ColorPickerDialog()
            val bundle = Bundle()
            bundle.putInt(INITIAL_COLOR, initialColor)
            bundle.putInt(CURRENT_COLOR, initialColor)
            dialog.arguments = bundle
            return dialog
        }

        private val TAG = ColorPickerDialog::class.java.simpleName
    }

    fun setBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = requireActivity().layoutInflater
            .inflate(R.layout.color_picker_dialog_view, null)
        currentColorView = dialogView.findViewById(R.id.color_picker_current_color_view)
        pipetteBtn = dialogView.findViewById(R.id.color_picker_pipette_btn)
        pipetteBtn.setOnClickListener {
            pipetteBtn.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                storeBitmapTemporally(currentBitmap, requireContext(), BITMAP_NAME)
                withContext(Dispatchers.Main) {
                    pipetteBtn.isEnabled = true
                    val intent = Intent(it.context, ColorPickerPreviewActivity::class.java).apply {
                        putExtra(COLOR_EXTRA, colorToApply)
                        putExtra(BITMAP_NAME_EXTRA, BITMAP_NAME)
                    }
                    try {
                        startActivityForResult(intent, REQUEST_CODE)
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "onCreateDialog: ${e.message}")
                    }
                }
            }
        }
        newColorView = dialogView.findViewById(R.id.color_picker_new_color_view)
        colorPickerView = dialogView.findViewById(R.id.color_picker_view)
        colorPickerView.setOnColorChangedListener(this)

        if (savedInstanceState != null) {
            setCurrentColor(savedInstanceState.getInt(CURRENT_COLOR, Color.BLACK))
            setInitialColor(savedInstanceState.getInt(INITIAL_COLOR, Color.BLACK))
        } else {
            val arguments = requireArguments()
            setCurrentColor(arguments.getInt(CURRENT_COLOR, Color.BLACK))
            setInitialColor(arguments.getInt(INITIAL_COLOR, Color.BLACK))
        }
        colorToApply = colorPickerView.initialColor
        val materialDialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setNegativeButton(R.string.color_picker_cancel) { dialogInterface: DialogInterface, _: Int ->
                updateColorChange(colorPickerView.initialColor)
                dialogInterface.dismiss()
            }
            .setPositiveButton(R.string.color_picker_apply) { _: DialogInterface, _: Int ->
                deleteBitmapFile(requireContext(), BITMAP_NAME)
                dismiss()
            }
            .setView(dialogView)
            .create()

        materialDialog.setOnShowListener {
            materialDialog.window?.clearFlags(FLAG_NOT_FOCUSABLE or FLAG_ALT_FOCUSABLE_IM)
            materialDialog.window?.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        return materialDialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val checkeredBitmap =
            BitmapFactory.decodeResource(resources, R.drawable.pocketpaint_checkeredbg)
        checkeredShader = BitmapShader(checkeredBitmap, TileMode.REPEAT, TileMode.REPEAT)
    }

    override fun colorChanged(color: Int) {
        setViewColor(newColorView, color)
        colorToApply = color
        updateColorChange(colorToApply)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CURRENT_COLOR, colorPickerView.getSelectedColor())
        outState.putInt(INITIAL_COLOR, colorPickerView.initialColor)
    }

    fun addOnColorPickedListener(listener: OnColorPickedListener) {
        onColorPickedListener.add(listener)
    }

    private fun updateColorChange(color: Int) {
        onColorPickedListener.forEach {
            it.colorChanged(color)
        }
    }

    private fun setInitialColor(color: Int) {
        setViewColor(currentColorView, color)
        colorPickerView.initialColor = color
    }

    private fun setCurrentColor(color: Int) {
        setViewColor(newColorView, color)
        colorPickerView.setSelectedColor(color)
    }

    override fun dismiss() {
        if (showsDialog) {
            super.dismiss()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    internal class CustomColorDrawable private constructor(
        checkeredShader: Shader,
        @ColorInt color: Int
    ) : ColorDrawable(color) {
        private var backgroundPaint: Paint? = null

        init {
            if (Color.alpha(getColor()) != FF) {
                backgroundPaint = Paint()
                backgroundPaint?.shader = checkeredShader
            }
        }

        companion object {
            @JvmStatic
            fun createDrawable(checkeredShader: Shader, @ColorInt color: Int): Drawable {
                return RippleDrawable(
                    ColorStateList.valueOf(Color.WHITE),
                    CustomColorDrawable(checkeredShader, color), null
                )
            }
        }

        override fun draw(canvas: Canvas) {
            backgroundPaint?.let {
                canvas.drawRect(bounds, it)
            }
            super.draw(canvas)
        }
    }

    private fun setViewColor(view: View, color: Int) {
        view.background = CustomColorDrawable.createDrawable(checkeredShader, color)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getIntExtra(COLOR_EXTRA, 0)?.let {
                setCurrentColor(it)
            }
        }
    }
}
