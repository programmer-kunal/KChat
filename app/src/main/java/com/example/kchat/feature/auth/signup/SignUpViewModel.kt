package com.example.kchat.feature.auth.signup
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.google.firebase.database.database
import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.google.firebase.auth.UserProfileChangeRequest
import com.example.kchat.SupabaseStorageUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

@HiltViewModel
class SignUpViewModel @Inject constructor() : ViewModel() {
    private val db = Firebase.database

    private val _state = MutableStateFlow<SignUpState>(SignUpState.Nothing)
    val state = _state.asStateFlow()
    fun signUp(
        name: String,
        email: String,
        password: String,
        imageUri: Uri?,
        context: Context
    ) {
        _state.value = SignUpState.Loading

        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val user = task.result.user ?: return@addOnCompleteListener

                    user.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                    )

                    viewModelScope.launch {
                        val storageUtils = SupabaseStorageUtils(context)

                        val imageUrl = imageUri?.let {
                            storageUtils.uploadImage(it)
                        }

                        Firebase.database.reference
                            .child("users")
                            .child(user.uid)
                            .setValue(
                                mapOf(
                                    "name" to name,
                                    "email" to email,
                                    "imageUrl" to imageUrl
                                )
                            )

                        user.sendEmailVerification()
                        FirebaseAuth.getInstance().signOut()
                        _state.value = SignUpState.Success
                    }

                } else {
                    _state.value = SignUpState.Error
                }
            }
    }

    fun signInWithGoogle(idToken: String) {
        _state.value = SignUpState.Loading
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
                                if (!hasProfile) {
                                    val userData = mapOf(
                                        "name" to (firebaseUser.displayName ?: "Google User"),
                                        "email" to (firebaseUser.email ?: ""),
                                        "imageUrl" to (firebaseUser.photoUrl?.toString() ?: "")
                                    )
                                    userRef.updateChildren(userData)
                                        .addOnCompleteListener { dbTask ->
                                            if (dbTask.isSuccessful) {
                                                _state.value = SignUpState.Success
                                            } else {
                                                _state.value = SignUpState.Error
                                            }
                                        }
                                } else {
                                    _state.value = SignUpState.Success
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                _state.value = SignUpState.Error
                            }
                        })
                    } else {
                        _state.value = SignUpState.Error
                    }
                } else {
                    _state.value = SignUpState.Error
                }
            }
    }

}
sealed class SignUpState{
    object Nothing : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    object Error : SignUpState()

}