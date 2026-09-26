package com.example.kchat.feature.auth.signin

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.kchat.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SignInScreen(navController: NavController) {

    val viewModel: SignInViewModel = hiltViewModel()
    val uiState = viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var lastClickTime by remember { mutableLongStateOf(0L) }

    // Google Sign-In setup
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("906912010882-p4e5he2c8o1uos9sruumcfhk8l4snn6l.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, googleSignInOptions) }

    // Pre-clear any cached Google session on entering the screen so chooser opens instantly
    LaunchedEffect(Unit) {
        googleSignInClient.signOut()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrEmpty()) {
                viewModel.loginWithGoogle(idToken = idToken)
            } else {
                viewModel.resetState()
                Toast.makeText(context, "Google Login failed: Empty token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            viewModel.resetState()
            // 12501 code or 16 is user cancellation - remain cleanly on LoginScreen
            if (e.statusCode != 12501 && e.statusCode != 16) {
                Toast.makeText(context, "Google Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            viewModel.resetState()
            Toast.makeText(context, "Google Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.value) {
        when (val state = uiState.value) {
            is SignInState.Success -> {
                if (state.isNewUser) {
                    Toast.makeText(context, "Welcome to KChat!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Welcome back to KChat!", Toast.LENGTH_SHORT).show()
                }
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            }
            is SignInState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 1. KChat Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "KChat Logo",
                    modifier = Modifier.size(118.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Branding: Title & Subtitle
                Text(
                    text = "KChat",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "AI That Understands Your Conversations.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Information Description
                Text(
                    text = "KChat is an AI-powered intelligent real-time communication platform designed to make conversations smarter, more meaningful, and secure.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4. AI Feature Highlights (2x2 Grid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AiFeatureCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "Smart Reply",
                        description = "Generate natural replies based on tone, sentiment, intent and context.",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AiFeatureCard(
                        icon = Icons.Default.Description,
                        title = "Thread Summary",
                        description = "Turn long conversations into key points, decisions, action items and deadlines.",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AiFeatureCard(
                        icon = Icons.Default.Search,
                        title = "Context Search",
                        description = "Find specific information and answers from your conversations using natural-language queries.",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AiFeatureCard(
                        icon = Icons.Default.Security,
                        title = "Scam Guard",
                        description = "Detect phishing, fraud, impersonation, suspicious links and other scam indicators.",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Login with Google Button / Loading State
                if (uiState.value is SignInState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = {
                            val currentTime = SystemClock.elapsedRealtime()
                            if (currentTime - lastClickTime < 1000L) {
                                return@Button
                            }
                            lastClickTime = currentTime

                            // Initiate asynchronous signOut without blocking chooser launch
                            googleSignInClient.signOut()
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.DarkGray else MaterialTheme.colorScheme.surface,
                            contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        border = if (!isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Login with Google",
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                softWrap = true
            )
        }
    }
}