package com.smartstudy.planning.dto;

import com.smartstudy.planning.dto.response.TaskResponse;
import com.smartstudy.planning.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "taskId", source = "id")
    @Mapping(target = "isCompleted", source = "completed")
    @Mapping(target = "date", source = "scheduledDate")
    TaskResponse toResponse(Task task);
}
