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
                categoryRepository.save(new Category(null, "Medications", "Pharmaceutical drugs and medicines", null));
                categoryRepository.save(new Category(null, "Medical Supplies", "Consumable medical supplies", null));
                categoryRepository.save(new Category(null, "Equipment", "Medical equipment and devices", null));
                System.out.println("✅ Categories initialized!");
            }

            // Always recreate users to ensure consistent password encoding
            if (userRepository.count() > 0) {
                System.out.println("⚠️ Deleting existing users to recreate with consistent password encoding");
                userRepository.deleteAll();
            }

            // Initialize users with encoded passwords
            if (userRepository.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123")); // IMPORTANT: Encoding password
                admin.setEmail("admin@medicorex.com");
                admin.setFullName("System Administrator");
                admin.setRole(User.UserRole.HOSPITAL_MANAGER);
                admin.setActive(true);
                userRepository.save(admin);
                System.out.println("✅ Admin user created with encoded password");

                User staff = new User();
                staff.setUsername("staff");
                staff.setPassword(passwordEncoder.encode("staff123")); // IMPORTANT: Encoding password
                staff.setEmail("staff@medicorex.com");
                staff.setFullName("Pharmacy Staff");
                staff.setRole(User.UserRole.PHARMACY_STAFF);
                staff.setActive(true);
                userRepository.save(staff);
                System.out.println("✅ Staff user created with encoded password");

                User procurement = new User();
                procurement.setUsername("procurement");
                procurement.setPassword(passwordEncoder.encode("proc123")); // IMPORTANT: Encoding password
                procurement.setEmail("procurement@medicorex.com");
                procurement.setFullName("Procurement Officer");
                procurement.setRole(User.UserRole.PROCUREMENT_OFFICER);
                procurement.setActive(true);
                userRepository.save(procurement);
                System.out.println("✅ Procurement user created with encoded password");

                System.out.println("✅ All users initialized with encoded passwords!");
            }
        };
    }
}
