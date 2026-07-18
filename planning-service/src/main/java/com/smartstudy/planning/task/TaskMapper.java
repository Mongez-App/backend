package com.smartstudy.planning.task;

import com.smartstudy.planning.task.dto.TaskResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "taskId", source = "id")
    @Mapping(target = "isCompleted", source = "completed")
    @Mapping(target = "date", source = "scheduledDate")
    TaskResponse toResponse(Task task);
}
