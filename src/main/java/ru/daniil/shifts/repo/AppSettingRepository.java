package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
