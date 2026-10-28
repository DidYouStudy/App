// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.event

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class EventsRepositoryProviderTest {

    @After
    fun tearDown() {
        EventsRepositoryProvider.setRepository(null)
    }

    @Test
    fun getRepository_defaultsToInMemoryRepository() {
        EventsRepositoryProvider.setRepository(null)

        assertTrue(EventsRepositoryProvider.getRepository() is InMemoryEventsRepository)
    }

    @Test
    fun getRepository_returnsTheSameInstanceOnEveryCall() {
        EventsRepositoryProvider.setRepository(null)

        assertSame(EventsRepositoryProvider.getRepository(), EventsRepositoryProvider.getRepository())
    }

    @Test
    fun setRepository_replacesTheSharedRepository() {
        val replacement = mock(EventsRepository::class.java)

        EventsRepositoryProvider.setRepository(replacement)

        assertSame(replacement, EventsRepositoryProvider.getRepository())
    }

    @Test
    fun setRepositoryToNull_makesNextCallCreateAFreshDefault() {
        val first = EventsRepositoryProvider.getRepository()

        EventsRepositoryProvider.setRepository(null)

        assertNotSame(first, EventsRepositoryProvider.getRepository())
    }
}