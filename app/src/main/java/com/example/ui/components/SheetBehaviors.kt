package com.example.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Prevents vertical scrolling and fling momentum inside a ModalBottomSheet
 * from bubbling up to the sheet's swipe-to-dismiss gesture handler.
 */
@Composable
fun rememberSheetScrollLockConnection(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset = available

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity
        ): Velocity = available
    }
}

/**
 * Creates a ModalBottomSheet SheetState that stays open when scrolling,
 * dragging, or tapping outside, so forms only close on explicit user actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNonDismissibleSheetState(): SheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { nextValue -> nextValue != SheetValue.Hidden }
)
