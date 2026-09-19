package com.example.kchat.feature.auth.signin

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.database
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor() : ViewModel(){
    private val _state = MutableStateFlow<SignInState>(SignInState.Nothing)
    val state = _state.asStateFlow()

    fun signIn(email:String,password:String){
        _state.value= SignInState.Loading
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email,password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        if (user.isEmailVerified) {
                            _state.value = SignInState.Success
                        } else {
                            // Resend verification email as a helper
                            user.sendEmailVerification()
                            FirebaseAuth.getInstance().signOut()
                            _state.value = SignInState.Unverified
                        }
                    } else {
                        _state.value = SignInState.Error
                    }
                } else {
                    _state.value = SignInState.Error
                }
            }
    }

    fun signInWithGoogle(idToken: String) {
        _state.value = SignInState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    if (firebaseUser != null) {
                        val uid = firebaseUser.uid
                        val db = Firebase.database
                        val userRef = db.reference.child("users").child(uid)

                        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val hasProfile = snapshot.child("email").exists() || snapshot.child("name").exists()
                                if (!hasProfile) {
                                    val userData = mapOf(
                                        "name" to (firebaseUser.displayName ?: "Google User"),
                                        "email" to (firebaseUser.email ?: ""),
                                        "imageUrl" to (firebaseUser.photoUrl?.toString() ?: "")
                                    )
                                    userRef.updateChildren(userData)
                                        .addOnCompleteListener { dbTask ->
                                            if (dbTask.isSuccessful) {
                                                _state.value = SignInState.Success
                                            } else {
                                                _state.value = SignInState.Error
                                            }
                                        }
                                } else {
                                    _state.value = SignInState.Success
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                _state.value = SignInState.Error
                            }
                        })
                    } else {
                        _state.value = SignInState.Error
                    }
                } else {
                    _state.value = SignInState.Error
                }
            }
    }
}

sealed class SignInState{
    object Nothing : SignInState()
    object Loading : SignInState()
    object Success : SignInState()
    object Error : SignInState()
    object Unverified : SignInState()
}