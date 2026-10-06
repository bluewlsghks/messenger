package com.individual.messenger.controller;

import com.individual.messenger.dto.ConferenceDtos.*;
import com.individual.messenger.service.ConferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/conferences/{roomId}")
public class ConferenceController {
    private final ConferenceService conferences;
    public ConferenceController(ConferenceService conferences){this.conferences=conferences;}
    @GetMapping public View view(Principal principal,@PathVariable String roomId){return conferences.view(principal,roomId);}
    @PostMapping("/join") public View join(Principal principal,@PathVariable String roomId,@Valid @RequestBody Join request){return conferences.join(principal,roomId,request.clientId());}
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void command(Principal principal,@PathVariable String roomId,@Valid @RequestBody Command request){conferences.command(principal,roomId,request);}
}
