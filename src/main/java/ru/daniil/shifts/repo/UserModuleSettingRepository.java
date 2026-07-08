package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.UserModuleSetting;

import java.util.List;
import java.util.Optional;

public interface UserModuleSettingRepository extends JpaRepository<UserModuleSetting, Long> {
    List<UserModuleSetting> findByOwner(AppUser owner);
    Optional<UserModuleSetting> findByOwnerAndModuleKey(AppUser owner, String moduleKey);
}
