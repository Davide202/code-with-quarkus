package org.acme;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.acme.dto.AccountDTO;
import org.acme.dto.UserDTO;
import org.acme.service.UserService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Path("/user")
public class UserResource {

    @Inject
    private UserService userService;

    @GET
    public Uni<List<UserDTO>> listUsers(){
        return userService.listUsers();
    }

    @POST
    public Uni<UserDTO> insertUser(UserDTO userDTO) throws ExecutionException, InterruptedException {
        return userService.insert(userDTO);
    }
}
