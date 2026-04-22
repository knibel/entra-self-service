package com.knibel.entraselfservice.web;

import com.knibel.entraselfservice.model.CreateUserRequest;
import com.knibel.entraselfservice.model.UpdateEmailRequest;
import com.knibel.entraselfservice.service.GraphProvisioningService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SelfServiceController {

    private final GraphProvisioningService provisioningService;

    public SelfServiceController(GraphProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @GetMapping("/")
    String index(Model model) {
        if (!model.containsAttribute("createUserRequest")) {
            model.addAttribute("createUserRequest", new CreateUserRequest("", "", "", "", ""));
        }
        if (!model.containsAttribute("updateEmailRequest")) {
            model.addAttribute("updateEmailRequest", new UpdateEmailRequest("", ""));
        }
        return "index";
    }

    @PostMapping("/users")
    String createUser(
        @Valid @ModelAttribute("createUserRequest") CreateUserRequest createUserRequest,
        BindingResult bindingResult,
        OAuth2AuthenticationToken principal,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("updateEmailRequest", new UpdateEmailRequest("", ""));
            return "index";
        }

        try {
            provisioningService.inviteUser(createUserRequest, principal);
            redirectAttributes.addFlashAttribute("successMessage", "Invitation sent for " + createUserRequest.email());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/users/email")
    String updateEmail(
        @Valid @ModelAttribute("updateEmailRequest") UpdateEmailRequest updateEmailRequest,
        BindingResult bindingResult,
        OAuth2AuthenticationToken principal,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("createUserRequest", new CreateUserRequest("", "", "", "", ""));
            return "index";
        }

        try {
            provisioningService.updatePrimaryEmail(updateEmailRequest, principal);
            redirectAttributes.addFlashAttribute("successMessage", "Email updated and invitation re-sent to " + updateEmailRequest.newEmail());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/";
    }
}
