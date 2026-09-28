package sg.edu.nus.cats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.edu.nus.cats.domain.CourseCatalogue;

public interface CourseCatalogueRepository extends JpaRepository<CourseCatalogue, Long> {

    @Query("""
            select c from CourseCatalogue c
            left join fetch c.provider
            join fetch c.category
            where c.active = true
              and (:query is null or lower(c.title) like lower(concat('%', :query, '%')))
            order by c.title asc
            """)
    List<CourseCatalogue> searchActive(@Param("query") String query);

    @Query("select c from CourseCatalogue c join fetch c.category left join fetch c.provider order by c.title asc")
    List<CourseCatalogue> findAllWithDetails();

    List<CourseCatalogue> findByActiveTrueOrderByTitleAsc();

    boolean existsByProviderId(Long providerId);
}
