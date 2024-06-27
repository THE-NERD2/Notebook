package org.notebook

import com.formdev.flatlaf.FlatLightLaf
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.symmetric.AES
import dev.whyoleg.cryptography.algorithms.symmetric.SymmetricKeySize
import net.miginfocom.swing.MigLayout
import java.io.*
import javax.swing.*

private val aesGcm = CryptographyProvider.Default.get(AES.GCM)
private lateinit var frame: JFrame
private lateinit var ui: UI
private var workingFile: WorkingFile? = null

private data class WorkingFile(val file: File, val type: Type) {
    enum class Type {
        PLAIN_TEXT, SERIALIZED
    }
}
private class EncryptedStorage(text: String, password: String, key: AES.GCM.Key): Serializable {
    private val encryptedText = key.cipher().encryptBlocking(text.encodeToByteArray()).toCollection(arrayListOf())
    private val encryptedPassword = key.cipher().encryptBlocking(password.encodeToByteArray()).toCollection(arrayListOf())
    private val encodedKey = key.encodeToBlocking(AES.Key.Format.RAW).toCollection(arrayListOf())
    fun getText(password: String): String? {
        val cipher = aesGcm.keyDecoder().decodeFromBlocking(AES.Key.Format.RAW, encodedKey.toByteArray()).cipher()
        if(password == cipher.decryptBlocking(encryptedPassword.toByteArray()).decodeToString()) {
            return cipher.decryptBlocking(encryptedText.toByteArray()).decodeToString()
        }
        return null
    }
}
private class UI: JPanel(MigLayout("nogrid")) {
    val textArea = JTextArea()
    init {
        add(JScrollPane(textArea), "center, push, grow")
    }
}

private fun typeForm(then: (WorkingFile.Type) -> Unit) {
    val dialog = JDialog(frame, "Open file")
    dialog.layout = MigLayout("fill", "[][]", "[][][][]")

    dialog.add(JLabel("Open file"), "cell 0 0, spanx, gapbottom 5")

    val textRadio = JRadioButton("Text file")
    dialog.add(textRadio, "cell 0 1, spanx 2, growx")

    val serRadio = JRadioButton("Serialized file")
    dialog.add(serRadio, "cell 0 2, spanx 2, growx")

    textRadio.addActionListener {
        serRadio.isSelected = false
    }
    serRadio.addActionListener {
        textRadio.isSelected = false
    }

    val wrongLabel = JLabel()
    wrongLabel.font = wrongLabel.font.deriveFont(9f)
    dialog.add(wrongLabel, "cell 0 3, spanx")

    val okBtn = JButton("OK")
    dialog.add(okBtn, "cell 1 4")
    okBtn.addActionListener {
        if(textRadio.isSelected) {
            then(WorkingFile.Type.PLAIN_TEXT)
        } else if(serRadio.isSelected) {
            then(WorkingFile.Type.SERIALIZED)
        } else {
            wrongLabel.text = "Nothing selected"
            return@addActionListener
        }
        dialog.isVisible = false
    }

    dialog.pack()
    dialog.isVisible = true
}
private fun passwordForm(then: (String) -> Boolean) {
    val dialog = JDialog(frame, "Enter file password")
    dialog.layout = MigLayout()

    dialog.add(JLabel("Enter file password"), "cell 0 0, spanx")

    dialog.add(JLabel("Password:"), "cell 0 1")

    val passwordField = JPasswordField()
    dialog.add(passwordField, "cell 1 1, spanx 2")

    val wrongLabel = JLabel()
    wrongLabel.font = wrongLabel.font.deriveFont(9f)
    dialog.add(wrongLabel, "cell 0 2, spanx")

    val okBtn = JButton("OK")
    dialog.add(okBtn, "cell 2 3")
    okBtn.addActionListener {
        if(then(passwordField.password.contentToString())) {
            dialog.isVisible = false
        } else {
            wrongLabel.text = "Wrong password"
        }
    }

    dialog.pack()
    dialog.isVisible = true
}
fun main() {
    FlatLightLaf.setup()
    SwingUtilities.invokeLater {
        frame = JFrame("Notebook")
        ui = UI()

        val menuBar = JMenuBar()
        val menu = JMenu("File")
        val open = JMenuItem("Open")
        open.addActionListener {
            typeForm {
                val fc = JFileChooser("~")
                if(fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    if (it == WorkingFile.Type.PLAIN_TEXT) {
                        workingFile = WorkingFile(File(fc.selectedFile.absolutePath), WorkingFile.Type.PLAIN_TEXT)
                        ui.textArea.text = workingFile!!.file.readText()
                    } else {
                        workingFile = WorkingFile(File(fc.selectedFile.absolutePath), WorkingFile.Type.SERIALIZED)
                        val finput = FileInputStream(workingFile!!.file)
                        val oinput = ObjectInputStream(finput)
                        val encryptedStorage = oinput.readObject() as EncryptedStorage
                        oinput.close()
                        finput.close()

                        passwordForm {
                            val txt = encryptedStorage.getText(it)
                            if(txt is String) {
                                ui.textArea.text = txt
                                return@passwordForm true
                            } else {
                                return@passwordForm false
                            }
                        }
                    }
                }
            }
        }
        val saveAs = JMenuItem("Save as")
        saveAs.addActionListener {
            typeForm {
                val fc = JFileChooser("~")
                if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    if (it == WorkingFile.Type.PLAIN_TEXT) {
                        workingFile = WorkingFile(File(fc.selectedFile.absolutePath), WorkingFile.Type.PLAIN_TEXT)
                        workingFile!!.file.writeText(ui.textArea.text)
                    } else {
                        workingFile = WorkingFile(File(fc.selectedFile.absolutePath), WorkingFile.Type.SERIALIZED)
                        passwordForm {
                            val generator = aesGcm.keyGenerator(SymmetricKeySize.B256)
                            val encryptedStorage = EncryptedStorage(ui.textArea.text, it, generator.generateKeyBlocking())

                            val foutput = FileOutputStream(workingFile!!.file)
                            val ooutput = ObjectOutputStream(foutput)
                            ooutput.writeObject(encryptedStorage)
                            ooutput.close()
                            foutput.close()
                            return@passwordForm true
                        }
                    }
                }
            }
        }
        val save = JMenuItem("Save")
        save.addActionListener {
            if(workingFile is WorkingFile) {
                if(workingFile!!.type == WorkingFile.Type.PLAIN_TEXT) {
                    workingFile!!.file.writeText(ui.textArea.text)
                } else {
                    passwordForm {
                        val generator = aesGcm.keyGenerator(SymmetricKeySize.B256)
                        val encryptedStorage = EncryptedStorage(ui.textArea.text, it, generator.generateKeyBlocking())

                        val foutput = FileOutputStream(workingFile!!.file)
                        val ooutput = ObjectOutputStream(foutput)
                        ooutput.writeObject(encryptedStorage)
                        ooutput.close()
                        foutput.close()
                        return@passwordForm true
                    }
                }
            }
        }
        menu.add(open)
        menu.add(saveAs)
        menu.add(save)
        menuBar.add(menu)
        frame.jMenuBar = menuBar

        frame.setSize(800, 600)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = ui
        frame.isVisible = true
    }
}