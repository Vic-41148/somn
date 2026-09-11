package dev.vic41148.somn.feature.alarm.captcha.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask
import kotlin.random.Random

class SequenceCaptchaTask : CaptchaTask {
    override val id: String = "sequence"
    override val displayName: String = "Type Sequence"
    
    private var targetSequence by mutableStateOf("")
    private var isSolved by mutableStateOf(false)

    init {
        generateSequence()
    }

    private fun generateSequence() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // O, I, 0, 1 removed for clarity
        targetSequence = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        isSolved = false
    }

    override fun isComplete(): Boolean = isSolved

    override fun reset() {
        generateSequence()
    }

    @Composable
    override fun TaskUI(onComplete: () -> Unit) {
        var userInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Type the sequence exactly to dismiss the alarm",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = targetSequence,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    letterSpacing = 4.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = userInput,
                onValueChange = { 
                    userInput = it.uppercase()
                    error = false
                },
                label = { Text("Sequence") },
                modifier = Modifier.fillMaxWidth(),
                isError = error,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (userInput == targetSequence) {
                        isSolved = true
                        onComplete()
                    } else {
                        error = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
        }
    }
}
