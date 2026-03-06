package nu.staldal.mycal.data.api

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("api/v1/events")
    suspend fun listEvents(
        @Query("from") from: String,
        @Query("to") to: String,
    ): Response<List<EventDto>>

    @GET("api/v1/events")
    suspend fun searchEvents(
        @Query("q") query: String,
    ): Response<List<EventDto>>

    @GET("api/v1/events/{id}")
    suspend fun getEvent(
        @Path("id", encoded = true) id: String,
    ): Response<EventDto>

    @POST("api/v1/events")
    suspend fun createEvent(
        @Body request: CreateEventRequest,
    ): Response<EventDto>

    @PATCH("api/v1/events/{id}")
    suspend fun updateEvent(
        @Path("id", encoded = true) id: String,
        @Body request: UpdateEventRequest,
    ): Response<EventDto>

    @DELETE("api/v1/events/{id}")
    suspend fun deleteEvent(
        @Path("id", encoded = true) id: String,
    ): Response<Unit>

    @GET("api/v1/calendars")
    suspend fun getCalendars(): Response<List<CalendarDto>>

    @PATCH("api/v1/calendars/{id}")
    suspend fun updateCalendar(
        @Path("id") id: Int,
        @Body request: UpdateCalendarRequest,
    ): Response<CalendarDto>
}
