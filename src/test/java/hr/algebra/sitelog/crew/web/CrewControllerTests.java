package hr.algebra.sitelog.crew.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.service.CrewCommand;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.crew.service.CrewView;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(CrewController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "ADMIN")
class CrewControllerTests extends WebSliceTest {

	@MockitoBean
	CrewService crewService;

	@Test
	void list_rendersAllCrewsInTheLayout() throws Exception {
		when(crewService.list()).thenReturn(List.of(new CrewView(1L, "Kovac Formwork", "Kovac d.o.o.", "Formwork", true),
				new CrewView(2L, "Old Rebar", "Rebar d.o.o.", "Rebar", false)));

		mvc.perform(get("/crews"))
			.andExpect(status().isOk())
			.andExpect(view().name(CrewController.LIST_VIEW))
			.andExpect(model().attribute("crews", hasSize(2)))
			.andExpect(model().attribute("currentUser", notNullValue()))
			.andExpect(content().string(containsString("Kovac Formwork")))
			.andExpect(content().string(containsString("Old Rebar")));
	}

	@Test
	@WithAnonymousUser
	void list_whenNotLoggedIn_redirectsToLogin() throws Exception {
		mvc.perform(get("/crews"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
		verifyNoInteractions(crewService);
	}

	@Test
	@WithAnonymousUser
	void create_whenNotLoggedIn_neverReachesTheService() throws Exception {
		mvc.perform(post("/crews").with(csrf()).param("name", "A").param("company", "B").param("trade", "C"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
		verifyNoInteractions(crewService);
	}

	@Test
	void create_withoutCsrfToken_isForbidden() throws Exception {
		mvc.perform(post("/crews").param("name", "Alpha").param("company", "Co").param("trade", "Formwork"))
			.andExpect(status().isForbidden());
		verifyNoInteractions(crewService);
	}

	@Test
	void create_withValidForm_redirectsToListWithSuccessFlash() throws Exception {
		when(crewService.create(any())).thenReturn(5L);

		mvc.perform(post("/crews").with(csrf())
			.param("name", " Alpha ")
			.param("company", "Gradnja")
			.param("trade", "Formwork")
			.param("active", "true"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute(Flash.SUCCESS, "crew.flash.created"))
			.andExpect(flash().attribute(Flash.DETAIL, org.hamcrest.Matchers.nullValue()))
			.andExpect(flash().attribute(Flash.ARGS, org.hamcrest.Matchers.arrayContaining("Alpha")));
		verify(crewService).create(new CrewCommand("Alpha", "Gradnja", "Formwork", true));
	}

	@Test
	void create_withBlankName_reRendersFormWithFieldErrorAndSavesNothing() throws Exception {
		mvc.perform(post("/crews").with(csrf()).param("name", " ").param("company", "Gradnja").param("trade", "Formwork"))
			.andExpect(status().isOk())
			.andExpect(view().name(CrewController.FORM_VIEW))
			.andExpect(model().attributeHasFieldErrorCode("crewForm", "name", "NotBlank"))
			.andExpect(model().errorCount(1))
			.andExpect(content().string(containsString("Name is required")));
		verify(crewService, never()).create(any());
	}

	@Test
	void create_whenNameIsTaken_showsTheServiceErrorOnTheNameField() throws Exception {
		when(crewService.create(any())).thenThrow(new InvalidFieldException("name", CrewService.NAME_DUPLICATE));

		mvc.perform(post("/crews").with(csrf()).param("name", "Alpha").param("company", "Co").param("trade", "Formwork"))
			.andExpect(status().isOk())
			.andExpect(view().name(CrewController.FORM_VIEW))
			.andExpect(model().attributeHasFieldErrorCode("crewForm", "name", CrewService.NAME_DUPLICATE))
			.andExpect(content().string(containsString("Name is already used")));
	}

	@Test
	void editForm_loadsCrewIntoTheForm() throws Exception {
		when(crewService.get(1L)).thenReturn(new CrewView(1L, "Alpha", "Co", "Formwork", false));

		mvc.perform(get("/crews/1/edit"))
			.andExpect(status().isOk())
			.andExpect(view().name(CrewController.FORM_VIEW))
			.andExpect(model().attribute("crewId", 1L))
			.andExpect(model().attribute("crewForm", hasProperty("active", is(false))));
	}

	@Test
	void editForm_whenCrewMissing_rendersOwn404Page() throws Exception {
		when(crewService.get(99L)).thenThrow(new NotFoundException(Crew.class, 99L));

		mvc.perform(get("/crews/99/edit"))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"));
	}

	@Test
	void update_withValidForm_redirectsWithUpdatedFlash() throws Exception {
		mvc.perform(post("/crews/1").with(csrf()).param("name", "Alpha").param("company", "Co").param("trade", "Rebar"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute(Flash.SUCCESS, "crew.flash.updated"))
			.andExpect(flash().attribute(Flash.DETAIL, org.hamcrest.Matchers.nullValue()))
			.andExpect(flash().attribute(Flash.ARGS, org.hamcrest.Matchers.arrayContaining("Alpha")));
		verify(crewService).update(eq(1L), any());
	}

	@Test
	void update_withInvalidForm_keepsCrewIdForTheFormAction() throws Exception {
		mvc.perform(post("/crews/1").with(csrf()).param("name", "Alpha").param("company", "").param("trade", "Rebar"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("crewId", 1L))
			.andExpect(model().attributeHasFieldErrors("crewForm", "company"));
		verify(crewService, never()).update(any(), any());
	}

	@Test
	void delete_ofUnusedCrew_redirectsWithDeletedFlash() throws Exception {
		mvc.perform(post("/crews/1/delete").with(csrf()))
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute(Flash.SUCCESS, "crew.flash.deleted"));
		verify(crewService).delete(1L);
	}

	@Test
	void delete_ofCrewInUse_redirectsWithErrorFlashTellingToDeactivate() throws Exception {
		doThrow(new DeletionNotAllowedException(CrewService.DELETE_BLOCKED, "in use")).when(crewService).delete(1L);

		mvc.perform(post("/crews/1/delete").with(csrf()))
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute(Flash.ERROR, CrewService.DELETE_BLOCKED))
			.andExpect(flash().attributeCount(1));
	}

}
