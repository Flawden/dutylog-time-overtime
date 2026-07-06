package ru.daniil.shifts.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;

import java.util.List;

/** При первом запуске (пустая БД) создаёт стандартный набор смен. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedShiftTypes(ShiftTypeRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            repo.saveAll(List.of(
                    new ShiftType("Дневная",  8,  "#F5B841", true),
                    new ShiftType("Ночная",   8,  "#7B8CE0", true),
                    new ShiftType("12 часов", 12, "#4FA3A5", true),
                    new ShiftType("5 часов",  5,  "#C97BB8", true),
                    new ShiftType("Выходной", 0,  "#5A6270", true)
            ));
        };
    }
}
