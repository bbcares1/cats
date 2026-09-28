package sg.edu.nus.cats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.TrainingProvider;

public interface TrainingProviderRepository extends JpaRepository<TrainingProvider, Long> {

    List<TrainingProvider> findByActiveTrueOrderByNameAsc();

    List<TrainingProvider> findAllByOrderByNameAsc();
}
