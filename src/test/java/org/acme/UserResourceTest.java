package org.acme;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.acme.dto.AccountDTO;
import org.acme.dto.UserDTO;
import org.junit.jupiter.api.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;
import static io.restassured.RestAssured.given;


@QuarkusTest
//@QuarkusTestResource(value = MySqlContainerConfig.class, restrictToAnnotatedClass = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserResourceTest {

//    @Inject
//    ObjectMapper objectMapper;

    @TestHTTPEndpoint(UserResource.class)
    @TestHTTPResource
    URL userURL;

    @Test
    @Order(1)
    public void post(){
        String uuid = UUID.randomUUID().toString();
        Response response = given()
                .contentType(ContentType.JSON)
                .body(buildUserDto(uuid)).post(userURL);
        response.then().statusCode(200);
        UserDTO responseBody = response.as(UserDTO.class);
        Assertions.assertEquals(
                uuid,
                responseBody.getNome()
        );
    }

    @Test
    @Order(2)
    public void get(){

        Response response = given()
                .contentType(ContentType.JSON)
                .get(userURL);
        response.then().statusCode(200);
        UserList responseBody = response.as(UserList.class);
//        UserDTO[] responseBody = response.as(UserDTO[].class);
        Assertions.assertNotNull(responseBody);
        Assertions.assertNotEquals(
                0,
                responseBody.size()
        );
    }

    private UserDTO buildUserDto(String uuid){
        AccountDTO account = new AccountDTO();
        account.setUsername(uuid);
        account.setPassword(uuid);
        UserDTO userDTO = new UserDTO();
        userDTO.setAccount(account);
        userDTO.setNome(uuid);
        userDTO.setCognome(uuid);
        return userDTO;
    }

    private static class UserList  extends ArrayList<UserDTO> { }

}
