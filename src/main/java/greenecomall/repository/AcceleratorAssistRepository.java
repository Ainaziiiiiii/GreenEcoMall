package greenecomall.repository;

import greenecomall.entity.AcceleratorAssist;
import greenecomall.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AcceleratorAssistRepository extends JpaRepository<AcceleratorAssist, UUID> {

    List<AcceleratorAssist> findByBeneficiaryOrderByCreatedAtDesc(User beneficiary);
}
