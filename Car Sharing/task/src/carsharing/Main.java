package carsharing;

import java.sql.*;
import java.util.*;

@FunctionalInterface
interface ObjectInstanceCreator<T> {
    T apply(ResultSet rs) throws SQLException;
}

class DbClient<T> {
    private final String databaseUrl;

    DbClient(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public void executeUpdateStatement(String sql) {
        try(
                Connection conn = DriverManager.getConnection(databaseUrl);
                Statement stmn = conn.createStatement();
        ) {
            stmn.executeUpdate(sql);
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public List<T> executeQueryStatement(String sql, ObjectInstanceCreator<T> objectInstanceCreator) {

        List<T> result = new ArrayList<>();

        try (
            Connection conn = DriverManager.getConnection(databaseUrl);
            Statement stmn = conn.createStatement();
        ) {
            try (ResultSet rs = stmn.executeQuery(sql)) {
                while(rs.next()) {
                    result.add(objectInstanceCreator.apply(rs));
                }
                return result;
            }
        } catch (SQLException se) {
            se.printStackTrace();
        }
        return null;
    }
}

class Company {

    final private String name;

    final private int id;

    Company(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }
}

interface ICompanyDao {

    void createCompany(String name);

    List<Company> getAllCompanies();

    Company getByName(String name);

    Company getById(int id);

}



class CompanyDao extends DbClient<Company> implements ICompanyDao {

    static private final ObjectInstanceCreator<Company> companyCreator = (ResultSet rs) ->  new Company(rs.getInt("id"), rs.getString("name"));

    CompanyDao() {
        super("jdbc:h2:./src/carsharing/db/carsharing");
        executeUpdateStatement("CREATE TABLE IF NOT EXISTS COMPANY(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255) UNIQUE NOT NULL)");
    }

    @Override
    public void createCompany(String name) {
        executeUpdateStatement(String.format("INSERT INTO COMPANY(NAME) values('%s')", name));
    }

    @Override
    public List<Company> getAllCompanies() {
        return executeQueryStatement(
                "SELECT ID, NAME FROM COMPANY ORDER BY ID",
                companyCreator
        );
    }

    @Override
    public Company getByName(String name) {
        List<Company> result =executeQueryStatement(
                String.format("SELECT ID, NAME FROM COMPANY WHERE NAME='%s' LIMIT 1", name),
                companyCreator
        );

        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public Company getById(int id) {
        List<Company> result =executeQueryStatement(
                String.format("SELECT ID, NAME FROM COMPANY WHERE ID=%d LIMIT 1", id),
                companyCreator
        );

        return result.isEmpty() ? null : result.get(0);
    }

}

class Car {
    final private int id;

    final private String name;

    final private int companyId;

    Car(int id, String name, int companyId) {
        this.id = id;
        this.name = name;
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public int getId() {return id;}

    public int getCompanyId() {
        return companyId;
    }
}

interface ICarDao {

    void createCar(String name, int companyId);

    List<Car> getCarsByCompanyId(int companyId);

    List<Car> getAvailableCarsByCompanyId(int companyId);

    Car getCarById(int id);

    Car getCarByName(String name);
}

class CarDao extends DbClient<Car> implements ICarDao {

    static private final ObjectInstanceCreator<Car> carCreator = (ResultSet rs) -> new Car(
            rs.getInt("ID"),
            rs.getString("NAME"),
            rs.getInt("COMPANY_ID")
    );

    CarDao() {
        super("jdbc:h2:./src/carsharing/db/carsharing");
        executeUpdateStatement(
                "CREATE TABLE IF NOT EXISTS car(" +
                        "ID INT AUTO_INCREMENT PRIMARY KEY," +
                        "NAME VARCHAR(250) NOT NULL UNIQUE," +
                        "COMPANY_ID INT NOT NULL," +
                        "CONSTRAINT FK_CAR_COMPANY FOREIGN KEY (COMPANY_ID) " +
                        "REFERENCES COMPANY(ID)" +
                        "ON DELETE CASCADE" +
                        ")"
        );
    }

    static public ObjectInstanceCreator<Car> getCarCreator() {
        return carCreator;
    }

    @Override
    public void createCar(String name, int companyId) {
        executeUpdateStatement(
                String.format("INSERT INTO CAR(NAME, COMPANY_ID) VALUES('%s', %s)", name, companyId)
        );
    }

    @Override
    public List<Car> getCarsByCompanyId(int companyId) {
        return executeQueryStatement(
                String.format(
                        "SELECT ID, NAME, COMPANY_ID FROM CAR WHERE COMPANY_ID = %d",
                        companyId
                ),
                carCreator
        );
    }

    @Override
    public List<Car> getAvailableCarsByCompanyId(int companyId) {
        return executeQueryStatement(
                String.format(
                        "SELECT CAR.ID as ID, CAR.NAME as NAME, CAR.COMPANY_ID as COMPANY_ID FROM CAR " +
                                "LEFT JOIN CUSTOMER ON CUSTOMER.RENTED_CAR_ID = CAR.ID "+
                                "WHERE CAR.COMPANY_ID = %d AND CUSTOMER.ID IS NULL",
                        companyId
                ),
                carCreator
        );
    }

    @Override
    public Car getCarById(int id) {
        List<Car> result =executeQueryStatement(
                String.format("SELECT ID, NAME, COMPANY_ID FROM CAR WHERE ID=%d LIMIT 1", id),
                carCreator
        );

        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public Car getCarByName(String name) {
        List<Car> result =executeQueryStatement(
                String.format("SELECT ID, NAME, COMPANY_ID FROM CAR WHERE NAME='%s' LIMIT 1", name),
                carCreator
        );

        return result.isEmpty() ? null : result.get(0);
    }
}

class Customer {
    final private int id;

    final private String name;

    private final Integer rentedCarId;

    Customer(int id, String name, Integer rentedCarId) {
        this.id = id;
        this.name = name;
        this.rentedCarId = rentedCarId;
    }

    public String getName() {
        return name;
    }

    public Integer getRentedCarId() {
        return rentedCarId;
    }
}

interface ICustomerDao {
    void createCustomer(String name);

    void setCustomerRentedCarId(String name, Integer rentedCarId);

    Customer getCustomerByName(String name);

    List<Customer> getAllCustomers();
}

class CustomerDao extends DbClient<Customer> implements ICustomerDao {

    static private final ObjectInstanceCreator<Customer> customerCreator = (ResultSet rs) -> new Customer(
            rs.getInt("ID"),
            rs.getString("NAME"),
            rs.getInt("RENTED_CAR_ID") == 0 ? null : rs.getInt("RENTED_CAR_ID")
    );

    CustomerDao() {
        super("jdbc:h2:./src/carsharing/db/carsharing");
        executeUpdateStatement(
                "CREATE TABLE IF NOT EXISTS CUSTOMER(" +
                        "ID INT AUTO_INCREMENT PRIMARY KEY," +
                        "NAME VARCHAR(250) NOT NULL UNIQUE," +
                        "RENTED_CAR_ID INT," +
                        "CONSTRAINT FK_CUSTOMER_CAR FOREIGN KEY (RENTED_CAR_ID) " +
                        "REFERENCES CAR(ID)" +
                        "ON DELETE CASCADE" +
                        ")"
        );
    }

    @Override
    public void createCustomer(String name) {
        executeUpdateStatement(
                String.format("INSERT INTO CUSTOMER(NAME) VALUES('%s')", name)
        );
    }

    @Override
    public void setCustomerRentedCarId(String name, Integer rentedCarId) {
        executeUpdateStatement(
                rentedCarId != null ?
                        String.format("UPDATE CUSTOMER SET RENTED_CAR_ID = %d WHERE NAME = '%s'", rentedCarId, name):
                        String.format("UPDATE CUSTOMER SET RENTED_CAR_ID = NULL WHERE NAME = '%s'", name)
        );
    }

    @Override
    public Customer getCustomerByName(String name) {
        List<Customer> result =executeQueryStatement(
                String.format("SELECT ID, NAME, RENTED_CAR_ID FROM CUSTOMER WHERE NAME='%s' LIMIT 1", name),
                customerCreator
        );

        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public List<Customer> getAllCustomers() {
        return executeQueryStatement(
                "SELECT ID, NAME, RENTED_CAR_ID FROM CUSTOMER ORDER BY ID",
                customerCreator
        );
    }
}

@FunctionalInterface
interface OptionActionCallback {
    void callback();
}

class Option {

    final private String description;

    private OptionActionCallback action = () -> System.out.println("No action defined");

    Option(String description) {
        this.description = description;
    }

    Option(String description, OptionActionCallback action) {
        this.description = description;
        this.action = action;
    }

    public String getDescription() {
        return description;
    }

    public OptionActionCallback getAction() {
        return action;
    }

    public void setAction(OptionActionCallback value) {
        action = value;
    }
}

class EmptyMenuException extends Exception {
    public EmptyMenuException(String message) {
        super(message);
    }
}


class OptionsMenu {

    private List<Option> optionsList;

    private String title = "Choose an option: ";

    private String emptyMessage = "No options assigned";

    private OptionsMenu parentMenu;

    private final List<OptionsMenu> subMenus = new ArrayList<>();

    private Integer selectedOption;

    private final static Scanner scanner = new Scanner(System.in);

    OptionsMenu(List<Option> optionsList) {this.optionsList = new ArrayList<>(optionsList);}

    OptionsMenu(String title, List<Option> optionsList) {
        this.title = title;
        this.optionsList = new ArrayList<>(optionsList);
    }

    OptionsMenu(String title, List<Option> optionsList, String emptyMessage) {
        this.title = title;
        this.optionsList = new ArrayList<>(optionsList);
        this.emptyMessage = emptyMessage;
    }

    OptionsMenu(List<Option> optionsList, String emptyMessage) {
        this.optionsList = new ArrayList<>(optionsList);
        this.emptyMessage = emptyMessage;
    }

    private void read() {

        if(scanner.hasNextInt()) {
            selectedOption = scanner.nextInt();
        }
        while (selectedOption == null || !(selectedOption >= 0 && selectedOption <= optionsList.size())) {
            scanner.nextLine();
            System.out.println("invalid input, retry");
            if(scanner.hasNextInt()) {
                selectedOption = scanner.nextInt();
            }
        }

    }



    private void print() throws NullPointerException, EmptyMenuException {

        System.out.println();

        if(optionsList.isEmpty()) throw new EmptyMenuException(emptyMessage);

        if(title != null) {
            System.out.println(title);
        }

        System.out.println();

        for(int i = 0; i < optionsList.size(); i++) {
            System.out.println((i + 1) + ". " + optionsList.get(i).getDescription());
        }

        System.out.println("0. " + (this.parentMenu != null ? "Back" : "Exit") );
    }

    public void show() throws RuntimeException {
        try {
            print();
            read();
            if(selectedOption > 0) {
                optionsList.get(selectedOption-1).getAction().callback();
            } else {
                if(parentMenu != null) parentMenu.show();
            }
        } catch (EmptyMenuException e) {
            System.out.println(e.getMessage());
            if(parentMenu != null) parentMenu.show();
        }
    }

    public List<Option> getOptions() {
        return Collections.unmodifiableList(optionsList);
    }

    public Integer getSelectedOption() {
        return selectedOption;
    }

    public void addSubMenu(int optionIndex, OptionsMenu subMenuValue) throws IndexOutOfBoundsException {
        optionsList.get(optionIndex).setAction(subMenuValue::show);
        subMenuValue.parentMenu = this;
        subMenus.add(subMenuValue);
    }

    public void addSubMenu(int optionIndex, OptionsMenu subMenuValue, OptionActionCallback beforeShow) throws IndexOutOfBoundsException {
        optionsList.get(optionIndex).setAction(() -> {
            beforeShow.callback();
            subMenuValue.show();
        });
        subMenuValue.parentMenu = this;
        subMenus.add(subMenuValue);
    }

    public void addSubMenu(int optionIndex, OptionsMenu subMenuValue, OptionActionCallback beforeShow, OptionsMenu parent) throws IndexOutOfBoundsException {
        optionsList.get(optionIndex).setAction(() -> {
            beforeShow.callback();
            subMenuValue.show();
        });
        subMenuValue.parentMenu = parent;
        subMenus.add(subMenuValue);
    }

    public void setParentMenu(OptionsMenu menu) {
        this.parentMenu = menu;
    }

    public void addOption(Option option) {
        optionsList.add(option);
    }

    public void setOptionsList(List<Option> optionsList) {this.optionsList = optionsList;}

    public void setTitle(String title) {
        this.title = title;
    }
}


public class Main {


    static private final CompanyDao companyDao = new CompanyDao();
    static private final CarDao carDao = new CarDao();

    static private final CustomerDao customerDao = new CustomerDao();

    static private Company currentCompany;

    static private Customer currentCustomer;

    static private final OptionsMenu initial = new OptionsMenu(
            "Welcome select an option: ",
            List.of(
                    new Option("Log in as a manager"),
                    new Option("Log in as a customer"),
                    new Option("Create a customer")
            )
    );

    static private final OptionsMenu manager = new OptionsMenu(
            List.of(
                    new Option("Company list"),
                    new Option("Create a company")
            )
    );

    static private final OptionsMenu companiesManager = new OptionsMenu(
            "Choose a company: ",
            companyDao.getAllCompanies()
                    .stream()
                    .map(e -> new Option(e.getName()))
                    .toList(),
            "The company list is empty!"
    );

    static private final OptionsMenu companiesCustomer = new OptionsMenu(
            "Choose a company: ",
            companiesManager.getOptions(),
            "The company list is empty!"
    );

    static private final OptionsMenu customers = new OptionsMenu(
            "Choose a customer: ",
            customerDao.getAllCustomers()
                    .stream()
                    .map(e -> new Option(e.getName()))
                    .toList(),
            "The customer list is empty!"
    );

    static private final OptionsMenu cars = new OptionsMenu(
            List.of(
                    new Option("Car list"),
                    new Option("Create a car")
            )
    );

    static private final OptionsMenu rentedCars = new OptionsMenu(
            List.of(
                    new Option("Rent a car"),
                    new Option("Return a rented car"),
                    new Option("My rented car")
            )
    );

    static List<Option> getAllCompaniesAsOptionsList() {
        return companyDao.getAllCompanies()
                .stream()
                .map(e -> new Option(e.getName()))
                .toList();
    }

    static List<Option> getAllCustomersAsOptionsList() {
        return customerDao.getAllCustomers()
                .stream()
                .map(e -> new Option(e.getName()))
                .toList();
    }

    static private void beforeShowCarsMenuCallback() {
        currentCompany = companyDao.getByName(
                companiesManager.getOptions().get(companiesManager.getSelectedOption() - 1).getDescription()
        );
        cars.setTitle("'" + currentCompany.getName() + "' company: ");
    }

    static private void beforeShowRentedCarsCallback() {
        currentCustomer = customerDao.getCustomerByName(
                customers.getOptions().get(customers.getSelectedOption() - 1).getDescription()
        );
        rentedCars.setTitle("Welcome '" + currentCustomer.getName() + "': ");
    }


    static private void createCompanyCallback() {
        System.out.println();
        System.out.println("Enter the company name:");
        String name = new java.util.Scanner(System.in).nextLine();
        companyDao.createCompany(name);
        System.out.println("The company was created!");
        companiesManager.setOptionsList(getAllCompaniesAsOptionsList());
        for(int i = 0; i < companiesManager.getOptions().size(); i++) {
            companiesManager.addSubMenu(i, cars, Main::beforeShowCarsMenuCallback, manager);
        }
        companiesCustomer.setOptionsList(getAllCompaniesAsOptionsList());
        for(int i = 0; i < companiesCustomer.getOptions().size(); i++) {
            companiesCustomer.addSubMenu(i, rentedCars, Main::beforeShowRentedCarsCallback, manager);
        }
        manager.show();
    }

    static private void createCustomerCallback() {
        System.out.println();
        System.out.println("Enter the customer name:");
        String name = new java.util.Scanner(System.in).nextLine();
        customerDao.createCustomer(name);
        System.out.println("The customer was created!");
        Option newCustomerOption = new Option(name);
        customers.setOptionsList(getAllCustomersAsOptionsList());
        for(int i = 0; i < customers.getOptions().size(); i++) {
            customers.addSubMenu(i, rentedCars, Main::beforeShowRentedCarsCallback, initial);
        }
        initial.show();
    }

    static private void showRentedCarsCallBack() {
        Company selectedCompany = companyDao.getByName(
                companiesCustomer.getOptions().get(companiesCustomer.getSelectedOption() - 1).getDescription()
        );

        OptionsMenu carsSelector = new OptionsMenu(
                "Choose a car: ",
                carDao.getAvailableCarsByCompanyId(selectedCompany.getId())
                        .stream()
                        .map((e) -> new Option(e.getName()))
                        .toList(),
                "No available cars!"
        );
        carsSelector.setParentMenu(rentedCars);
        for(Option option: carsSelector.getOptions()) {
            option.setAction(() -> {
                Car rentedCar = carDao.getCarByName(
                        carsSelector.getOptions().get(carsSelector.getSelectedOption() - 1).getDescription()
                );
                customerDao.setCustomerRentedCarId(currentCustomer.getName(), rentedCar.getId());
                currentCustomer = customerDao.getCustomerByName(currentCustomer.getName());
                System.out.println("You rented '" + rentedCar.getName()  + "'");
                rentedCars.show();
            });
        }
        carsSelector.show();
    }

    static private void rentCarCallback() {
        companiesCustomer.setParentMenu(rentedCars);
        if(currentCustomer.getRentedCarId() != null) {
            System.out.println();
            System.out.println("You've already rented a car!");
            rentedCars.show();
        } else {
            for(Option option: companiesCustomer.getOptions()) {
                option.setAction(Main::showRentedCarsCallBack);
            }
            companiesCustomer.show();
        }
    }

    static private void returnRentedCarCallback() {
        System.out.println();
        if(currentCustomer.getRentedCarId() != null) {
            customerDao.setCustomerRentedCarId(
                    currentCustomer.getName(),
                    null
            );
            currentCustomer = customerDao.getCustomerByName(currentCustomer.getName());
            System.out.println("You've returned a rented car!");
        } else {
            System.out.println("You didn't rent a car!");
        }
        rentedCars.show();;
    }

    static private void printRentedCarCallback() {
        System.out.println();
        if(currentCustomer.getRentedCarId() != null) {
            Car rentedCar = carDao.getCarById(currentCustomer.getRentedCarId());
            Company company = companyDao.getById(rentedCar.getCompanyId());
            System.out.println("You rented car:");
            System.out.println(rentedCar.getName());
            System.out.println("Company:");
            System.out.println(company.getName());
        } else {
            System.out.println("You didn't rent a car!");
        }
        rentedCars.show();
    }

    static private void printCarListCallback() {
        System.out.println();
        System.out.println("'" + currentCompany.getName() + "' cars:");
        List<Car> carsList = carDao.getCarsByCompanyId(currentCompany.getId());
        if(!carsList.isEmpty()) {
            for(int i = 0; i < carsList.size(); i++) {
                System.out.println((i + 1) + ". " + carsList.get(i).getName());
            }
        } else {
            System.out.println("The car list is empty!");
        }
        cars.show();
    }

    static private void createCarCallback() {
        System.out.println();
        System.out.println("Enter the car name:");
        String name = new java.util.Scanner(System.in).nextLine();
        carDao.createCar(name, currentCompany.getId());
        System.out.println("The car was added!");
        cars.show();
    }




    public static void main(String[] args) {
        try {
            initial.addSubMenu(0, manager);

            initial.addSubMenu(1, customers);

            initial.getOptions().get(2).setAction(Main::createCustomerCallback);

            manager.addSubMenu(0, companiesManager);

            manager.getOptions().get(1).setAction(Main::createCompanyCallback);

            for(int i = 0; i < companiesManager.getOptions().size(); i++) {
                companiesManager.addSubMenu(i, cars, Main::beforeShowCarsMenuCallback, manager);
            }

            for(int i = 0; i < customers.getOptions().size(); i++) {
                customers.addSubMenu(i, rentedCars, Main::beforeShowRentedCarsCallback, initial);
            }

            cars.getOptions().get(0).setAction(Main::printCarListCallback);

            cars.getOptions().get(1).setAction(Main::createCarCallback);

            rentedCars.getOptions().get(0).setAction(Main::rentCarCallback);

            rentedCars.getOptions().get(1).setAction(Main::returnRentedCarCallback);

            rentedCars.getOptions().get(2).setAction(Main::printRentedCarCallback);

            // initialization
            initial.show();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

    }
}

