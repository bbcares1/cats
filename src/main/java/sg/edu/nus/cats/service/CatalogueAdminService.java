package sg.edu.nus.cats.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.CourseCatalogue;
import sg.edu.nus.cats.domain.CourseCategory;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.PublicHoliday;
import sg.edu.nus.cats.domain.TrainingProvider;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.dto.CatalogueForm;
import sg.edu.nus.cats.dto.HolidayForm;
import sg.edu.nus.cats.dto.ProviderForm;
import sg.edu.nus.cats.repository.CourseCatalogueRepository;
import sg.edu.nus.cats.repository.CourseCategoryRepository;
import sg.edu.nus.cats.repository.PublicHolidayRepository;
import sg.edu.nus.cats.repository.TrainingProviderRepository;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;

/**
 * Reference data: training providers, the course catalogue and the public holiday calendar.
 * Deactivation is used instead of deletion so historic applications keep their links.
 */
@Service
public class CatalogueAdminService {

    private final TrainingProviderRepository providers;
    private final CourseCatalogueRepository catalogue;
    private final CourseCategoryRepository categories;
    private final PublicHolidayRepository holidays;
    private final AuditService audit;
    private final Clock clock;

    public CatalogueAdminService(TrainingProviderRepository providers, CourseCatalogueRepository catalogue,
            CourseCategoryRepository categories, PublicHolidayRepository holidays, AuditService audit, Clock clock) {
        this.providers = providers;
        this.catalogue = catalogue;
        this.categories = categories;
        this.holidays = holidays;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ providers */

    @Transactional(readOnly = true)
    public List<TrainingProvider> allProviders() {
        return providers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public TrainingProvider requireProvider(Long id) {
        return providers.findById(id).orElseThrow(() -> BusinessException.notFound("Training provider"));
    }

    @Transactional
    public TrainingProvider saveProvider(ProviderForm form, Employee actor) {
        TrainingProvider provider;
        if (form.getId() == null) {
            provider = new TrainingProvider();
        } else {
            provider = requireProvider(form.getId());
            if (form.getVersion() != null && form.getVersion() != provider.getVersion()) {
                throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This provider changed since the page was opened. Reload and reapply the change.");
            }
        }
        provider.setName(form.getName().trim());
        provider.setActive(form.isActive());
        providers.save(provider);
        audit.record(AggregateType.CATALOGUE, provider.getName(), AuditEventType.CATALOGUE_CHANGED, actor, null, null,
                (form.getId() == null ? "Created provider " : "Updated provider ") + provider.getName(), null);
        return provider;
    }

    @Transactional
    public void setProviderActive(Long id, boolean active, Employee actor) {
        TrainingProvider provider = requireProvider(id);
        if (!active && catalogue.existsByProviderId(id)) {
            throw new BusinessException(ErrorCode.ACCOUNT_IN_USE,
                    provider.getName() + " is still referenced by the course catalogue");
        }
        provider.setActive(active);
        providers.save(provider);
        audit.record(AggregateType.CATALOGUE, provider.getName(), AuditEventType.CATALOGUE_CHANGED, actor, null, null,
                "Provider " + provider.getName() + (active ? " activated" : " deactivated"), null);
    }

    /* ------------------------------------------------------------------ catalogue */

    @Transactional(readOnly = true)
    public List<CourseCatalogue> allCourses() {
        return catalogue.findAllWithDetails();
    }

    /** Active courses that an employee may choose from, optionally filtered by keyword. */
    @Transactional(readOnly = true)
    public List<CourseCatalogue> searchCourses(String query) {
        String needle = query == null || query.isBlank() ? null : query.trim();
        return needle == null ? catalogue.findByActiveTrueOrderByTitleAsc() : catalogue.searchActive(needle);
    }

    @Transactional(readOnly = true)
    public CourseCatalogue requireCourse(Long id) {
        return catalogue.findById(id).orElseThrow(() -> BusinessException.notFound("Course"));
    }

    @Transactional(readOnly = true)
    public List<CourseCategory> allCategories() {
        return categories.findAll();
    }

    @Transactional(readOnly = true)
    public CourseCategory requireCategory(CategoryCode code) {
        return categories.findById(code).orElseThrow(() -> BusinessException.notFound("Course category"));

    }

    @Transactional
    public CourseCatalogue saveCourse(CatalogueForm form, Employee actor) {
        CourseCatalogue course;
        if (form.getId() == null) {
            course = new CourseCatalogue();
        } else {
            course = requireCourse(form.getId());
            if (form.getVersion() != null && form.getVersion() != course.getVersion()) {
                throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This provider changed since the page was opened. Reload and reapply the change.");
            }
        }
        CourseCategory category = requireCategory(form.getCategoryCode());
        TrainingProvider provider = requireProvider(form.getProviderId());
        if (!provider.isActive() && (course.getProvider() == null
                || !provider.getId().equals(course.getProvider().getId()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    provider.getName() + " is deactivated and cannot be selected for a new course");
        }
        course.setCategory(category);
        course.setProvider(provider);
        course.setTitle(form.getTitle().trim());
        course.setDefaultFee(category.getCode().requiresFee()
                ? form.getDefaultFee() == null ? java.math.BigDecimal.ZERO : form.getDefaultFee()
                : java.math.BigDecimal.ZERO);
        course.setDescription(form.getDescription());
        course.setActive(form.isActive());
        catalogue.save(course);
        audit.record(AggregateType.CATALOGUE, course.getTitle(), AuditEventType.CATALOGUE_CHANGED, actor, null, null,
                (form.getId() == null ? "Created course " : "Updated course ") + course.getTitle(), null);
        return course;
    }

    @Transactional
    public void setCourseActive(Long id, boolean active, Employee actor) {
        CourseCatalogue course = requireCourse(id);
        course.setActive(active);
        catalogue.save(course);
        audit.record(AggregateType.CATALOGUE, course.getTitle(), AuditEventType.CATALOGUE_CHANGED, actor, null, null,
                "Course " + course.getTitle() + (active ? " activated" : " deactivated"), null);
    }

    /* ------------------------------------------------------------------ holidays */

    @Transactional(readOnly = true)
    public List<PublicHoliday> holidaysIn(int year) {
        return holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public PublicHoliday requireHoliday(LocalDate date) {
        return holidays.findById(date).orElseThrow(() -> BusinessException.notFound("Public holiday"));
    }

    @Transactional
    public PublicHoliday saveHoliday(HolidayForm form, Employee actor) {
        LocalDate date = form.getHolidayDate();
        java.util.Optional<PublicHoliday> existing = holidays.findById(date);
        PublicHoliday holiday = existing.orElseGet(PublicHoliday::new);
        holiday.setHolidayDate(date);
        holiday.setName(form.getName().trim());
        holiday.setSourceNote(form.getSourceNote());
        holiday.setUpdatedAt(clock.instant());
        holidays.save(holiday);
        audit.record(AggregateType.HOLIDAY, date.toString(), AuditEventType.HOLIDAY_CHANGED, actor, null, null,
                (existing.isPresent() ? "Updated holiday " : "Added holiday ") + date + " " + holiday.getName(), null);
        return holiday;
    }

    @Transactional
    public void deleteHoliday(LocalDate date, Employee actor) {
        PublicHoliday holiday = requireHoliday(date);
        holidays.delete(holiday);
        audit.record(AggregateType.HOLIDAY, date.toString(), AuditEventType.HOLIDAY_CHANGED, actor, null, null,
                "Removed holiday " + date + " " + holiday.getName(), null);
    }

}
