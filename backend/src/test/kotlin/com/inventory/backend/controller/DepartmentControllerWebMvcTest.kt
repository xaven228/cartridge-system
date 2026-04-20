package com.inventory.backend.controller

import com.inventory.backend.service.DepartmentService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DepartmentController::class)
class DepartmentControllerWebMvcTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var departmentService: DepartmentService

    @MockBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Test
    fun createShouldReturnBadRequestForMalformedJson() {
        mockMvc.perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("name"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Некорректный формат JSON в теле запроса"))
    }
}
