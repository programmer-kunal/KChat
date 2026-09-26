package com.example.kchat.feature.auth.signin

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor() : ViewModel() {
    private val db = Firebase.database

    private val _state = MutableStateFlow<SignInState>(SignInState.Nothing)
    val state = _state.asStateFlow()

    fun resetState() {
        _state.value = SignInState.Nothing
    }

    fun loginWithGoogle(idToken: String) {
        _state.value = SignInState.Loading

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    if (firebaseUser != null) {
                        val uid = firebaseUser.uid
                        val userRef = db.reference.child("users").child(uid)

                        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val hasProfile = snapshot.child("email").exists() || snapshot.child("name").exists()
                                if (hasProfile) {
                                    // EXISTING KCHAT USER:
                                    // Preserve existing username, profile photo, about, etc.
                                    _state.value = SignInState.Success(isNewUser = false)
                                } else {
                                    // NEW KCHAT USER:
                                    // Automatically use Google display name and Google profile photo
                                    val finalUsername = firebaseUser.displayName ?: "Google User"
                                    val finalImageUrl = firebaseUser.photoUrl?.toString() ?: ""

                                    val userData = mapOf(
                                        "name" to finalUsername,
                                        "email" to (firebaseUser.email ?: ""),
                                        "imageUrl" to finalImageUrl
                                    )

                                    userRef.updateChildren(userData).addOnCompleteListener { dbTask ->
                                        if (dbTask.isSuccessful) {
                                            if (finalUsername.isNotEmpty()) {
                                                firebaseUser.updateProfile(
                                                    UserProfileChangeRequest.Builder()
                                                        .setDisplayName(finalUsername)
                                                        .build()
                                                )
                                            }
                                            _state.value = SignInState.Success(isNewUser = true)
                                        } else {
                                            _state.value = SignInState.Error(
                                                dbTask.exception?.localizedMessage ?: "Failed to set up user profile."
                                            )
                                        }
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                _state.value = SignInState.Error(error.message)
                            }
                        })
                    } else {
                        _state.value = SignInState.Error("Google Login failed: User not found.")
                    }
                } else {
                    val err = task.exception?.localizedMessage ?: "Google Login failed."
                    _state.value = SignInState.Error(err)
                }
            }
    }
}

sealed class SignInState {
    object Nothing : SignInState()
    object Loading : SignInState()
    data class Success(val isNewUser: Boolean) : SignInState()
    data class Error(val message: String) : SignInState()
}