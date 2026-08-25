package com.fearmikey.rf_reapr.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

data class WorkflowStep(
    val id: String,
    val title: String,
    val description: String,
    val route: String,
    val icon: ImageVector? = null,
    val isMandatory: Boolean = false
)

data class AppMode(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val securityWarning: String,
    val steps: List<WorkflowStep>,
    val requiresTarget: Boolean = false,
    val targetHint: String = "example.com"
)
