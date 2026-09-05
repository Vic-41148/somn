package dev.vic41148.somn.feature.alarm.captcha.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask
import kotlin.random.Random

class MathCaptchaTask : CaptchaTask {
    override val id: String = "math"
    override val displayName: String = "Math Problem"
    
    private var num1 by mutableIntStateOf(0)
    private var num2 by mutableIntStateOf(0)
    private var operator by mutableStateOf("+")
    private var expectedResult by mutableIntStateOf(0)
    private var isSolved by mutableStateOf(false)

    init {
        generateProblem()
    }

    private fun generateProblem() {
        num1 = Random.nextInt(10, 50)
        num2 = Random.nextInt(1, 20)
        val opType = Random.nextInt(3)
        when (opType) {
            0 -> {
                operator = "+"
                expectedResult = num1 + num2
            }
            1 -> {
                operator = "-"
                expectedResult = num1 - num2
            }
            2 -> {
                operator = "*"
                expectedResult = num1 * num2
            }
        }
        isSolved = false
    }

    override fun isComplete(): Boolean = isSolved

    override fun reset() {
        generateProblem()
    }

    @Composable
    override fun TaskUI(onComplete: () -> Unit) {
        var userInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Solve to dismiss the alarm",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$num1 $operator $num2 = ?",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = userInput,
                onValueChange = { 
                    userInput = it
                    error = null 
                },
                label = { Text("Result") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(200.dp),
                isError = error != null
            )
            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val result = userInput.toIntOrNull()
                    if (result == expectedResult) {
                        isSolved = true
                        onComplete()
                    } else {
                        error = "The answer is incorrect. Try again"
                        userInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
        }
    }
}
