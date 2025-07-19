package com.medicorex.config;

import com.medicorex.entity.Category;
import com.medicorex.entity.User;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner init(UserRepository userRepository, CategoryRepository categoryRepository) {
        return args -> {
            // Initialize categories
            if (categoryRepository.count() == 0) {
                // Create root categories
                Category medications = new Category();
                medications.setName("Medications");
                medications.setDescription("Pharmaceutical drugs and medicines");
                categoryRepository.save(medications);

                Category medicalSupplies = new Category();
                medicalSupplies.setName("Medical Supplies");
                medicalSupplies.setDescription("Consumable medical supplies");
                categoryRepository.save(medicalSupplies);

                Category equipment = new Category();
                equipment.setName("Equipment");
                equipment.setDescription("Medical equipment and devices");
                categoryRepository.save(equipment);

                System.out.println("✅ Categories initialized!");

                // Optional: Create some subcategories for demonstration
                Category antibiotics = new Category();
                antibiotics.setName("Antibiotics");
                antibiotics.setDescription("Antibiotic medications");
                antibiotics.setParent(medications);
                categoryRepository.save(antibiotics);

                Category painRelief = new Category();
                painRelief.setName("Pain Relief");
                painRelief.setDescription("Pain relief medications");
                painRelief.setParent(medications);
                categoryRepository.save(painRelief);

                Category syringes = new Category();
                syringes.setName("Syringes");
                syringes.setDescription("Medical syringes and needles");
                syringes.setParent(medicalSupplies);
                categoryRepository.save(syringes);

                System.out.println("✅ Subcategories initialized!");
            }

            // Initialize users only if they don't exist
            if (userRepository.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setEmail("admin@medicorex.com");
                admin.setFullName("System Administrator");
                admin.setRole(User.UserRole.HOSPITAL_MANAGER);
                admin.setActive(true);
                userRepository.save(admin);
                System.out.println("✅ Admin user created with encoded password");

                User staff = new User();
                staff.setUsername("staff");
                staff.setPassword(passwordEncoder.encode("staff123"));
                staff.setEmail("staff@medicorex.com");
                staff.setFullName("Pharmacy Staff");
                staff.setRole(User.UserRole.PHARMACY_STAFF);
                staff.setActive(true);
                userRepository.save(staff);
                System.out.println("✅ Staff user created with encoded password");

                User procurement = new User();
                procurement.setUsername("procurement");
                procurement.setPassword(passwordEncoder.encode("proc123"));
                procurement.setEmail("procurement@medicorex.com");
                procurement.setFullName("Procurement Officer");
                procurement.setRole(User.UserRole.PROCUREMENT_OFFICER);
                procurement.setActive(true);
                userRepository.save(procurement);
                System.out.println("✅ Procurement user created with encoded password");

                System.out.println("✅ All users initialized with encoded passwords!");
            } else {
                System.out.println("✅ Users already exist, skipping initialization");
            }
        };
    }
}