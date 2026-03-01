package nu.staldal.mycal.data.local

import nu.staldal.mycal.data.api.CreateEventRequest
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.UpdateEventRequest

fun EventDto.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    color = color,
    recurrenceFreq = recurrenceFreq,
    location = location,
    categories = categories,
    url = url,
    reminderMinutes = reminderMinutes,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EventEntity.toDto(): EventDto = EventDto(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    color = color,
    recurrenceFreq = recurrenceFreq,
    location = location,
    categories = categories,
    url = url,
    reminderMinutes = reminderMinutes,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EventEntity.toCreateRequest(): CreateEventRequest = CreateEventRequest(
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    color = color,
    location = location,
    reminderMinutes = reminderMinutes,
)

fun EventEntity.toUpdateRequest(): UpdateEventRequest = UpdateEventRequest(
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    color = color,
    location = location,
    reminderMinutes = reminderMinutes,
)
