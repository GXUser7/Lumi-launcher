package com.yumi.launcher.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** The system file dialog, pointed at a Java executable. */
fun pickJava(): String? {
    val dialog = FileDialog(null as Frame?, "Выберите javaw.exe", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.equals("javaw.exe", true) || name.equals("java.exe", true) || name == "java" }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file).path
}

/** A folder chooser. AWT's own dialog cannot pick folders on Windows, so this one is Swing's. */
fun pickFolder(start: File, title: String): File? {
    val chooser = JFileChooser(generateSequence(start) { it.parentFile }.firstOrNull { it.isDirectory }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        approveButtonText = "Выбрать"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Old versions run LWJGL 2 on Java 8, which cannot load natives from a path outside ASCII. */
fun File.hasNonAscii(): Boolean = path.any { it.code > 127 }

/** "120 ГБ свободно" for the drive [folder] is on, found through its nearest existing parent. */
fun freeSpace(folder: File): String? {
    val existing = generateSequence(folder) { it.parentFile }.firstOrNull { it.exists() } ?: return null
    val gb = existing.usableSpace / (1024L * 1024 * 1024)
    return "$gb ГБ свободно"
}
