package com.yourdomain;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.DatabaseInitializer;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.User;
import com.yourdomain.ui.AdminMenu;
import com.yourdomain.ui.Authentication;
import com.yourdomain.ui.ConsoleUtil;
import com.yourdomain.ui.CustomerPortal;
import com.yourdomain.ui.StaffMenu;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        DatabaseInitializer.initialize();

        ConsoleUtil in = new ConsoleUtil(new Scanner(System.in));
        Authentication auth = new Authentication(in, new UserDao(), new ActivityLogDao());

        System.out.println("=== GASOLINE STATION MANAGEMENT SYSTEM ===");
        System.out.println("(Default admin -> username: admin | password: admin123)");

        while (true) {
            System.out.println("\n1. Login");
            System.out.println("2. Register");
            System.out.println("0. Exit");
            int choice = in.readInt("Choice: ");
            switch (choice) {
                case 1 -> {
                    User user = auth.login();
                    if (user != null) {
                        if ("ADMIN".equals(user.role())) {
                            new AdminMenu(in, user).show();
                        } else if ("CUSTOMER".equals(user.role())) {
                            new CustomerPortal(in, user).show();
                        } else {
                            new StaffMenu(in, user).show();
                        }
                        auth.logout(user);
                    }
                }
                case 2 -> auth.register();
                case 0 -> {
                    System.out.println("Goodbye!");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }
}
