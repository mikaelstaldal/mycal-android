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
        @Path("id") id: Long,
    ): Response<EventDto>

    @POST("api/v1/events")
    suspend fun createEvent(
        @Body request: CreateEventRequest,
    ): Response<EventDto>

    @PUT("api/v1/events/{id}")
    suspend fun updateEvent(
        @Path("id") id: Long,
        @Body request: UpdateEventRequest,
    ): Response<EventDto>

    @DELETE("api/v1/events/{id}")
    suspend fun deleteEvent(
        @Path("id") id: Long,
    ): Response<Unit>
}
