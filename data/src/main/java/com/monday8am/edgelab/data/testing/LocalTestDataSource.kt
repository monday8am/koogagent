package com.monday8am.edgelab.data.testing

/** Interface that abstracts the local storage of test definitions. */
interface LocalTestDataSource {
    suspend fun getTests(): List<TestCaseDefinition>?

    suspend fun saveTests(tests: List<TestCaseDefinition>)
}
