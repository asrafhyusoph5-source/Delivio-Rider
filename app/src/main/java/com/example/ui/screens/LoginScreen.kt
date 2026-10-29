package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.models.RiderApplication
import com.example.ui.theme.DelivioRed
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.TextOnYellow
import com.example.viewmodel.RiderViewModel

@Composable
fun LoginScreen(
    viewModel: RiderViewModel,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isRegistering by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }

    val applicationState by viewModel.applicationState.collectAsState()
    val isBlockedMsg by viewModel.isBlocked.collectAsState()
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
                // If account is blocked or application in review
                isBlockedMsg != null -> {
                    BlockedStatusView(
                        message = isBlockedMsg ?: "",
                        onLogout = { viewModel.logout {} }
                    )
                }
                applicationState != null -> {
                    ApplicationStatusView(
                        application = applicationState!!,
                        viewModel = viewModel,
                        onLogout = { viewModel.logout {} }
                    )
                }
                isRegistering -> {
                    RegistrationFormView(
                        viewModel = viewModel,
                        onBackToLogin = { isRegistering = false }
                    )
                }
                else -> {
                    // Standard Login View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // App Logo
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

                        // Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email address") },
                            placeholder = { Text("driver@example.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        // Forgot password link
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Forgot password?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    forgotEmail = email
                                    showForgotPasswordDialog = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Sign In Button
                        Button(
                            onClick = {
                                viewModel.login(email, password) {
                                    // Callback
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("sign_in_button")
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Register as a Driver Link
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
                                modifier = Modifier.clickable { isRegistering = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your email address to receive a secure password reset link:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.forgotPassword(forgotEmail) {
                            showForgotPasswordDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Send Reset Link", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ================= REGISTRATION FORM VIEW =================

@Composable
private fun RegistrationFormView(
    viewModel: RiderViewModel,
    onBackToLogin: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToLogin) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Register as a Driver", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }

        Text("Fill out the application below. Once approved by admin, you will receive full dispatch access.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Text("Vehicle Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        // 4 vehicle types
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
                if (password != confirmPassword) {
                    viewModel.showSnackbar("Passwords do not match")
                    return@Button
                }
                if (!termsAccepted) {
                    viewModel.showSnackbar("Please accept the terms to proceed")
                    return@Button
                }
                if (fullName.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
                    viewModel.showSnackbar("Please fill in all required personal information")
                    return@Button
                }

                val seats = seatsText.toIntOrNull() ?: if (vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR8) 8 else if (vehicleType == com.example.data.constants.OrderStepConstants.VEHICLE_CAR4) 4 else 1

                viewModel.registerAsDriver(
                    name = fullName,
                    phone = phone,
                    email = email,
                    pass = password,
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
                    onSuccess = { onBackToLogin() }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
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
