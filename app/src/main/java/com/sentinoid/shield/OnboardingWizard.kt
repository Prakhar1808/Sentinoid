package com.sentinoid.shield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingWizard(mnemonic: String, onWizardComplete: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    val words = remember { mnemonic.split(" ") }
    val confirmationIndices = remember { (0 until words.size).shuffled().take(3).sorted() }
    val confirmationWords = remember { confirmationIndices.map { words[it] } }

    var inputWord1 by remember { mutableStateOf("") }
    var inputWord2 by remember { mutableStateOf("") }
    var inputWord3 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }


    Column(modifier = Modifier.padding(16.dp)) {
        when (step) {
            1 -> {
                Text("Welcome to Sentinoid!")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your recovery phrase is a 24-word phrase that you can use to recover your account if you lose access.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Write it down and store it in a safe place.")
                Spacer(modifier = Modifier.height(16.dp))
                Text(mnemonic)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { step = 2 }) {
                    Text("I've written it down")
                }
            }
            2 -> {
                Text("Confirm your recovery phrase.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Enter the words from your phrase corresponding to the numbers below.")
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputWord1,
                    onValueChange = { inputWord1 = it },
                    label = { Text("Word #${confirmationIndices[0] + 1}") }
                )
                OutlinedTextField(
                    value = inputWord2,
                    onValueChange = { inputWord2 = it },
                    label = { Text("Word #${confirmationIndices[1] + 1}") }
                )
                OutlinedTextField(
                    value = inputWord3,
                    onValueChange = { inputWord3 = it },
                    label = { Text("Word #${confirmationIndices[2] + 1}") }
                )

                if (error) {
                    Text("Incorrect words. Please try again.")
                }

                Button(onClick = {
                    val userWords = listOf(inputWord1, inputWord2, inputWord3)
                    if (userWords.map { it.trim().lowercase() } == confirmationWords.map { it.lowercase() }) {
                        onWizardComplete()
                    } else {
                        error = true
                    }
                }) {
                    Text("Confirm")
                }
            }
        }
    }
}