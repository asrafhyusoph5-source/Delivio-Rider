package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.models.RiderApplication
import com.example.ui.theme.DelivioRed
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.TextOnYellow
import com.example.viewmodel.RiderViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun LoginScreen(
    viewModel: RiderViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(activityContext) { CredentialManager.create(activityContext) }

    var isRegistering by remember { mutableStateOf(false) }

    val applicationState by viewModel.applicationState.collectAsState()
    val isBlockedMsg by viewModel.isBlocked.collectAsState()
    val needsRegistration by viewModel.needsRegistration.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val signedInEmail by viewModel.signedInUserEmail.collectAsState()
    val signedInName by viewModel.signedInUserName.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(needsRegistration) {
        if (needsRegistration) {
            isRegistering = true
        }
    }

    // One-shot silent auto sign-in attempt if no Firebase session is currently active
    LaunchedEffect(Unit) {
        val hasCurrentUser = try { FirebaseAuth.getInstance().currentUser != null } catch (_: Throwable) { false }
        if (!hasCurrentUser) {
            try {
                val serverClientId = activityContext.getString(R.string.default_web_client_id)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogle(googleCredential.idToken)
                }
            } catch (_: NoCredentialException) {
                // No previously authorized Google account available; user can tap Sign in with Google
            } catch (_: GetCredentialCancellationException) {
                // Silent attempt dismissed
            } catch (e: GetCredentialException) {
                Log.d("Auth", "Silent Google Sign-In skipped: ${e.message}")
            } catch (t: Throwable) {
                Log.d("Auth", "Silent Google Sign-In unavailable: ${t.message}")
            }
        }
    }

    fun launchInteractiveGoogleSignIn(openRegistrationIfNew: Boolean = false) {
        if (isAuthLoading) return
        viewModel.setAuthLoading(true)
        coroutineScope.launch {
            try {
                val serverClientId = activityContext.getString(R.string.default_web_client_id)
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogle(googleCredential.idToken) {
                        if (openRegistrationIfNew) {
                            isRegistering = true
                        }
                    }
                } else {
                    viewModel.setAuthLoading(false)
                    viewModel.showSnackbar("Unexpected credential type received from Google Sign-In")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                viewModel.setAuthLoading(false)
                viewModel.showSnackbar("Google Sign-In was cancelled. Please select an account to continue.")
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No Google credentials found on device: ${e.message}", e)
                viewModel.setAuthLoading(false)
                viewModel.showSnackbar("No Google account found on this device. Please add a Google account and try again.")
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("Auth", "Invalid Google ID token response", e)
                viewModel.setAuthLoading(false)
                viewModel.showSnackbar("Could not verify Google account token.")
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Google Sign-In failed: ${e.message}", e)
                viewModel.setAuthLoading(false)
                viewModel.showSnackbar(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
            } catch (t: Throwable) {
                Log.e("Auth", "Unexpected Google Sign-In error", t)
                viewModel.setAuthLoading(false)
                viewModel.showSnackbar(t.localizedMessage ?: "Sign-In failed")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("login_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isBlockedMsg != null -> {
                    BlockedStatusView(
                        message = isBlockedMsg ?: "",
                        onLogout = {
                            isRegistering = false
                            viewModel.logout {}
                        }
                    )
                }
                applicationState != null -> {
                    ApplicationStatusView(
                        application = applicationState!!,
                        viewModel = viewModel,
                        onLogout = {
                            isRegistering = false
                            viewModel.logout {}
                        }
                    )
                }
                isRegistering || needsRegistration -> {
                    RegistrationFormView(
                        viewModel = viewModel,
                        initialName = signedInName,
                        initialEmail = signedInEmail,
                        onBackToLogin = {
                            isRegistering = false
                            viewModel.logout {}
                        }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(DelivioYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.delivio_rider_icon_1791915489146),
                                contentDescription = "Delivio Driver Logo",
                                modifier = Modifier.size(76.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Delivio Driver",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Driver & Courier Dispatch Platform",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Secure Driver Access",
                                    tint = DelivioYellow,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Sign in with your Google Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Access live dispatch offers, earnings, and rider tools securely.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { launchInteractiveGoogleSignIn(openRegistrationIfNew = false) },
                            enabled = !isAuthLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioYellow,
                                contentColor = TextOnYellow
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    color = TextOnYellow,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Signing in...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google Account",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("New driver? ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Register as a Driver",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DelivioYellow,
                                modifier = Modifier
                                    .clickable(enabled = !isAuthLoading) {
                                        launchInteractiveGoogleSignIn(openRegistrationIfNew = true)
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("register_driver_link")
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= REGISTRATION FORM VIEW =================

@Composable
private fun RegistrationFormView(
    viewModel: RiderViewModel,
    initialName: String,
    initialEmail: String,
    onBackToLogin: () -> Unit
) {
    var fullName by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf("") }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var cityId by remember { mutableStateOf("puerto_princesa") }
    var vehicleType by remember { mutableStateOf(com.example.data.constants.OrderStepConstants.VEHICLE_MOTORCYCLE) }
    var plate by remember { mutableStateOf("") }
    var vehicleModel by remember { mutableStateOf("") }
    var vehicleColor by remember { mutableStateOf("") }
    var seatsText by remember { mutableStateOf("4") }
    var vehiclePhotoUrl by remember { mutableStateOf("") }
    var franchiseUrl by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }

    var licenseUrl by remember { mutableStateOf("") }
    var orcrUrl by remember { mutableStateOf("") }
    var nbiUrl by remember { mutableStateOf("") }
    var validIdUrl by remember { mutableStateOf("") }
    var selfieUrl by remember { mutableStateOf("") }

    var termsAccepted by remember { mutableStateOf(false) }

    val isCar = vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR4 ||
            vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBackToLogin) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sign Out and Go Back")
            }
            Text(
                text = "Register as a Driver",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBackToLogin) {
                Text("Sign Out", color = DelivioYellow)
            }
        }

        Text(
            text = "Fill out the application below. Once approved by admin, you will receive full dispatch access.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            enabled = initialEmail.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Vehicle Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                com.example.data.constants.OrderStepConstants.VEHICLE_MOTORCYCLE to "Motorcycle",
                com.example.data.constants.OrderStepConstants.VEHICLE_TRICYCLE to "Tricycle"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = vehicleType == type,
                    onClick = {
                        vehicleType = type
                        seatsText = "1"
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                com.example.data.constants.OrderStepConstants.VEHICLE_CAR4 to "4 Seater Car",
                com.example.data.constants.OrderStepConstants.VEHICLE_CAR8 to "8 Seater Car"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = vehicleType == type,
                    onClick = {
                        vehicleType = type
                        seatsText = if (type == com.example.data.constants.OrderStepConstants.VEHICLE_CAR8) "8" else "4"
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Plate Number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = vehicleModel, onValueChange = { vehicleModel = it }, label = { Text("Vehicle Make & Model") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = vehicleColor, onValueChange = { vehicleColor = it }, label = { Text("Vehicle Color") }, modifier = Modifier.fillMaxWidth())

        if (isCar) {
            OutlinedTextField(
                value = seatsText,
                onValueChange = { seatsText = it.filter { c -> c.isDigit() } },
                label = { Text("Number of Seats") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(value = vehiclePhotoUrl, onValueChange = { vehiclePhotoUrl = it }, label = { Text("Vehicle Photo URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = franchiseUrl, onValueChange = { franchiseUrl = it }, label = { Text("Franchise Document URL (Optional)") }, modifier = Modifier.fillMaxWidth())

        Text("Emergency Contact", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(value = emergencyName, onValueChange = { emergencyName = it }, label = { Text("Contact Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emergencyPhone, onValueChange = { emergencyPhone = it }, label = { Text("Contact Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())

        Text("Compliance Document Image URLs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Provide accessible image URLs for your compliance documents:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it }, label = { Text("Driver's License URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = orcrUrl, onValueChange = { orcrUrl = it }, label = { Text("OR / CR Image URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = nbiUrl, onValueChange = { nbiUrl = it }, label = { Text("NBI Clearance URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = validIdUrl, onValueChange = { validIdUrl = it }, label = { Text("Government Valid ID URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = selfieUrl, onValueChange = { selfieUrl = it }, label = { Text("Driver Selfie Photo URL") }, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
            Spacer(modifier = Modifier.width(6.dp))
            Text("I agree to the Delivio Driver terms of service and safety guidelines.", fontSize = 12.sp)
        }

        Button(
            onClick = {
                if (!termsAccepted) {
                    viewModel.showSnackbar("Please accept the terms to proceed")
                    return@Button
                }
                if (fullName.isBlank() || phone.isBlank() || email.isBlank()) {
                    viewModel.showSnackbar("Please fill in all required personal information")
                    return@Button
                }

                val seats = seatsText.toIntOrNull() ?: if (vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR8) 8 else if (vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR4) 4 else 1

                viewModel.submitDriverApplication(
                    name = fullName,
                    phone = phone,
                    email = email,
                    cityId = cityId,
                    vehicleType = vehicleType,
                    plate = plate,
                    vehicleModel = vehicleModel,
                    vehicleColor = vehicleColor,
                    seats = seats,
                    vehiclePhotoUrl = vehiclePhotoUrl,
                    franchiseUrl = franchiseUrl.ifBlank { null },
                    emergencyName = emergencyName,
                    emergencyPhone = emergencyPhone,
                    licenseUrl = licenseUrl,
                    orcrUrl = orcrUrl,
                    nbiUrl = nbiUrl,
                    validIdUrl = validIdUrl,
                    selfieUrl = selfieUrl,
                    onSuccess = { }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_application_button")
        ) {
            Text("Submit Application", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ================= APPLICATION STATUS VIEW =================

@Composable
private fun ApplicationStatusView(
    application: RiderApplication,
    viewModel: RiderViewModel,
    onLogout: () -> Unit
) {
    var showResubmitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (icon, title, color) = when (application.status) {
            "Pending" -> Triple(Icons.Default.HourglassTop, "Application Submitted", DelivioYellow)
            "Under Review" -> Triple(Icons.Default.FactCheck, "Under Review", DelivioYellow)
            "Needs Info" -> Triple(Icons.Default.HelpOutline, "Action Required: Needs Info", DelivioYellow)
            "Rejected" -> Triple(Icons.Default.Cancel, "Application Rejected", DelivioRed)
            else -> Triple(Icons.Default.Info, application.status, DelivioYellow)
        }

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)

        Spacer(modifier = Modifier.height(8.dp))

        when (application.status) {
            "Pending" -> {
                Text(
                    text = "Application submitted. Wait for admin approval.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            "Under Review" -> {
                Text(
                    text = "Our operations team is currently reviewing your documents and fleet requirements. This usually takes 24-48 business hours.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            "Needs Info" -> {
                Text(
                    text = "Admin note: ${application.needsInfoReason.ifBlank { "Please update your documents or details." }}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showResubmitDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update & Resubmit Info", fontWeight = FontWeight.Bold)
                }
            }
            "Rejected" -> {
                Text(
                    text = "Reason: ${application.rejectionReason.ifBlank { "Requirements not met." }}",
                    fontSize = 14.sp,
                    color = DelivioRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) {
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }

    if (showResubmitDialog) {
        var resubmitPhone by remember { mutableStateOf(application.phone) }
        var resubmitModel by remember { mutableStateOf(application.vehicleModel) }
        var resubmitPlate by remember { mutableStateOf(application.plate) }
        var licenseUrl by remember { mutableStateOf(application.documents["licenseUrl"] ?: "") }
        var orcrUrl by remember { mutableStateOf(application.documents["orcrUrl"] ?: "") }
        var nbiUrl by remember { mutableStateOf(application.documents["nbiUrl"] ?: "") }
        var validIdUrl by remember { mutableStateOf(application.documents["validIdUrl"] ?: "") }
        var selfieUrl by remember { mutableStateOf(application.documents["selfieUrl"] ?: "") }

        AlertDialog(
            onDismissRequest = { showResubmitDialog = false },
            title = { Text("Resubmit Application", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = resubmitPhone, onValueChange = { resubmitPhone = it }, label = { Text("Phone") })
                    OutlinedTextField(value = resubmitModel, onValueChange = { resubmitModel = it }, label = { Text("Vehicle Model") })
                    OutlinedTextField(value = resubmitPlate, onValueChange = { resubmitPlate = it }, label = { Text("Plate") })
                    OutlinedTextField(value = licenseUrl, onValueChange = { licenseUrl = it }, label = { Text("License URL") })
                    OutlinedTextField(value = orcrUrl, onValueChange = { orcrUrl = it }, label = { Text("OR/CR URL") })
                    OutlinedTextField(value = nbiUrl, onValueChange = { nbiUrl = it }, label = { Text("NBI Clearance URL") })
                    OutlinedTextField(value = validIdUrl, onValueChange = { validIdUrl = it }, label = { Text("Valid ID URL") })
                    OutlinedTextField(value = selfieUrl, onValueChange = { selfieUrl = it }, label = { Text("Selfie URL") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resubmitApplication(
                            phone = resubmitPhone,
                            vehicleModel = resubmitModel,
                            plate = resubmitPlate,
                            licenseUrl = licenseUrl,
                            orcrUrl = orcrUrl,
                            nbiUrl = nbiUrl,
                            validIdUrl = validIdUrl,
                            selfieUrl = selfieUrl,
                            onSuccess = { showResubmitDialog = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResubmitDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ================= BLOCKED / SUSPENDED VIEW =================

@Composable
private fun BlockedStatusView(message: String, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Block, contentDescription = "Blocked", tint = DelivioRed, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Account Suspended or Inactive", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DelivioRed)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(12.dp)) {
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}
