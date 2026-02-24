package com.monday8am.edgelab.presentation.testing

/**
 * Exception thrown when a test run is cancelled by the user. Used to interrupt the reactive flow
 * and trigger a transition to the Idle state.
 */
class TestCancelledException : Exception("Test run cancelled by user")
