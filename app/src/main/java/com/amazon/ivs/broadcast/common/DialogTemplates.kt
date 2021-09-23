package com.amazon.ivs.broadcast.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.databinding.DialogCameraOptionsBinding
import com.amazon.ivs.broadcast.databinding.DialogInputBinding
import com.amazon.ivs.broadcast.databinding.DialogOrientationOptionsBinding
import com.amazon.ivs.broadcast.databinding.ItemOptionBinding
import com.amazon.ivs.broadcast.models.Orientation
import com.amazon.ivs.broadcast.models.ResolutionModel
import com.amazon.ivs.broadcast.models.ui.DeviceItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun View.showResolutionDialog(
    inputText: String?,
    switch: SwitchCompat? = null,
    onValueSet: (value: ResolutionModel) -> Unit,
) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val binding = DialogInputBinding.inflate(inflater)
    binding.input.hint = context?.getString(R.string.custom_resolution_hint)
    binding.description.text = context?.getString(R.string.custom_resolution_input_note)

    inputText?.let {
        if (inputText.isNotEmpty() && inputText != resources.getString(R.string.not_set)) {
            binding.input.setText(inputText)
        }
    }
    binding.input.requestFocus()
    binding.input.showSoftKeyboard()

    val dialog = MaterialAlertDialogBuilder(this.context, R.style.AlertDialog)
        .setView(binding.root)
        .setTitle(context?.getString(R.string.custom_resolution))
        .setPositiveButton(resources.getString(R.string.ok), null)
        .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
            switch?.let {
                switch.isChecked = false
            }
        }.show()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        try {
            val splitValue = binding.input.text.toString().lowercase().split("x")
            val width = splitValue[0].trim().toFloat()
            val height = splitValue[1].trim().toFloat()
            if (width > 1920f || width < 160f || height > 1920f || height < 160f) {
                binding.description.text = context?.getString(R.string.wrong_resolution_input)
                binding.description.setTextColor(ContextCompat.getColor(context, R.color.broadcast_primary_red_color))
            } else {
                onValueSet(ResolutionModel(width, height))
                dialog.dismiss()
            }
        } catch (formatException: Exception) {
            binding.description.text = context?.getString(R.string.wrong_resolution_input)
            binding.description.setTextColor(ContextCompat.getColor(context, R.color.broadcast_primary_red_color))
        }
    }

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun View.showFramerateDialog(
    inputText: String?,
    switch: SwitchCompat? = null,
    onValueSet: (value: Int) -> Unit,
) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val binding = DialogInputBinding.inflate(inflater)
    binding.input.hint = context?.getString(R.string.custom_framerate_hint)
    binding.description.text = context?.getString(R.string.custom_framerate_input_note)

    inputText?.let {
        if (inputText.isNotEmpty() && inputText != resources.getString(R.string.not_set)) {
            binding.input.setText(inputText)
        }
    }
    binding.input.requestFocus()
    binding.input.showSoftKeyboard()

    val dialog = MaterialAlertDialogBuilder(this.context, R.style.AlertDialog)
        .setView(binding.root)
        .setTitle(
            context?.getString(R.string.custom_framerate)
        )
        .setPositiveButton(resources.getString(R.string.ok), null)
        .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
            switch?.let {
                switch.isChecked = false
            }
        }.show()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        try {
            val fieldValue = binding.input.text.toString().toInt()
            if (fieldValue < 10 || fieldValue > 60) {
                binding.description.text = context?.getString(R.string.wrong_framerate_input)
                binding.description.setTextColor(ContextCompat.getColor(context, R.color.broadcast_primary_red_color))
            } else {
                onValueSet(fieldValue)
                dialog.dismiss()
            }
        } catch (formatException: java.lang.NumberFormatException) {
            binding.description.text = context?.getString(R.string.wrong_framerate_input)
            binding.description.setTextColor(ContextCompat.getColor(context, R.color.broadcast_primary_red_color))
        }
    }

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun View.showInputDialog(
    title: String,
    descriptionText: String = "",
    inputHint: String,
    inputText: String?,
    switch: SwitchCompat? = null,
    onValueSet: (value: String) -> Unit
) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val binding = DialogInputBinding.inflate(inflater)
    binding.input.hint = inputHint

    if (descriptionText.isEmpty()) {
        binding.description.setVisible(false)
    } else {
        binding.description.text = descriptionText
    }

    inputText?.let {
        if (inputText.isNotEmpty() && inputText != resources.getString(R.string.not_set)) {
            binding.input.setText(inputText)
        }
    }
    binding.input.requestFocus()
    binding.input.showSoftKeyboard()

    val dialog = MaterialAlertDialogBuilder(this.context, R.style.AlertDialog)
        .setView(binding.root)
        .setTitle(title)
        .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            val fieldValue = binding.input.text.toString()
            onValueSet(fieldValue)
        }
        .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
            switch?.let {
                switch.isChecked = false
            }
        }.show()

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun View.showOrientationOptions(
    title: String,
    orientation: Orientation,
    onValueSet: (value: Orientation) -> Unit
) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val binding = DialogOrientationOptionsBinding.inflate(inflater)
    val selectedOption = when (orientation) {
        Orientation.AUTO -> binding.optionAuto.id
        Orientation.LANDSCAPE -> binding.optionLandscape.id
        Orientation.PORTRAIT -> binding.optionPortrait.id
        Orientation.SQUARE -> binding.optionSquare.id
    }
    binding.radioGroup.check(selectedOption)

    var newOption = orientation
    binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
            binding.optionAuto.id -> newOption = Orientation.AUTO
            binding.optionLandscape.id -> newOption = Orientation.LANDSCAPE
            binding.optionPortrait.id -> newOption = Orientation.PORTRAIT
            binding.optionSquare.id -> newOption = Orientation.SQUARE
        }
    }

    MaterialAlertDialogBuilder(this.context, R.style.AlertDialog)
        .setView(binding.root)
        .setTitle(title)
        .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            onValueSet(newOption)
        }
        .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

fun View.showCameraDialog(
    title: String,
    devices: List<DeviceItem>?,
    onValueSet: (value: DeviceItem) -> Unit
) {
    var selectedDevice: DeviceItem? = null
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val dialogBinding = DialogCameraOptionsBinding.inflate(inflater)

    devices?.forEach { option ->
        val buttonBinding = ItemOptionBinding.inflate(inflater)
        val viewId = View.generateViewId()
        buttonBinding.root.id = viewId
        option.viewId = viewId
        buttonBinding.optionItem = option
        dialogBinding.radioGroup.addView(buttonBinding.root)
        if (option.isSelected) {
            dialogBinding.radioGroup.check(buttonBinding.root.id)
            selectedDevice = option
        }
    }

    dialogBinding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
        dialogBinding.radioGroup.children.forEach { radioButton ->
            if (radioButton.id == checkedId) {
                selectedDevice = devices?.first { it.viewId == radioButton.id }
            }
        }
    }

    MaterialAlertDialogBuilder(this.context, R.style.AlertDialog)
        .setView(dialogBinding.root)
        .setTitle(title)
        .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            selectedDevice?.let { option ->
                onValueSet(option)
            }
        }
        .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}
