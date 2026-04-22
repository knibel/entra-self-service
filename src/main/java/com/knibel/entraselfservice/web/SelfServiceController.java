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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class SelfServiceController {

    private static final Logger log = LoggerFactory.getLogger(SelfServiceController.class);

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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Create/invite request failed: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to create/invite user. Please verify the request and try again.");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while creating/inviting user", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Unexpected error while creating/inviting user.");
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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Email update request failed: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to update email/re-invite user. Please verify the request and try again.");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while updating user email", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Unexpected error while updating user email.");
        }
        return "redirect:/";
    }
}
