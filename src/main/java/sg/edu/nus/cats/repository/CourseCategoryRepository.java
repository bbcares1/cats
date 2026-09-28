package sg.edu.nus.cats.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.CourseCategory;
import sg.edu.nus.cats.domain.enums.CategoryCode;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, CategoryCode> {
}
