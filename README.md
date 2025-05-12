# Payment Method Selector

## 📌 About the Project
**Payment Method Selector** is an application that optimally assigns payment methods (loyalty points and bank cards) to a list of grocery orders, maximizing total discounts. Each order is also assigned a 
selected subset of possible promotions associated with the payment method.

Each order can be paid:

- in full by one traditional payment method (e.g. one card),
- in full with loyalty points,
- partially with points and partially with one traditional payment method.

**Discounts**:
- If the **entire** order is paid with a **card** of a bank with which we have an agreement (such payment methods are specified in the list of available promotions for a given order ), the percentage discount specified in the definition of the payment method is calculated.
- If the customer pays at least **10%** of the order value (before the discount) with loyalty **points**, the store gives an additional discount of 10% on the entire order value.
- If the **entire** order is paid with loyalty **points**, apply the discount defined for the “POINTS” method, instead of the 10% discount for the partial payment with points.

[Task description](2025%20Promocje%20dla%20metod%20płatności%20v2.pdf)

## 🚀 Setup & Installation

### Prerequisites
- **Java 21** (or 17)  
- **Maven**

### Cloning the Repository
```sh
git clone https://github.com/mateusz-plaska/Mateusz_Plaska_Java_Wroclaw.git
cd Mateusz_Plaska_Java_Wroclaw
```

## 2. Running
### Create a new JAR file:
#### Compile sources, run tests, and build a JAR:
```sh
mvn clean package
```
#### After build, the JAR is located at:
```sh
target/payment-method-selection-1.0-SNAPSHOT.jar
```
#### Run the JAR file:
```sh
java -jar target/payment-method-selection-1.0-SNAPSHOT.jar /path/to/orders.json /path/to/paymentmethods.json
```

### Run from available JAR file:
#### The JAR file is currently in the root directory, so you can run:
```sh
java -jar payment-method-selection-1.0.jar /path/to/orders.json /path/to/paymentmethods.json
```

## 🏗️ Testing
Run unit and integration tests with:
```sh
mvn test
```

## Usage Example
### Input
orders.json
```json
[
  {"id":"ORDER1","value":"100.00","promotions":["mZysk"]},
  {"id":"ORDER2","value":"200.00","promotions":["BosBankrut"]},
  {"id":"ORDER3","value":"150.00","promotions":["mZysk","BosBankrut"]},
  {"id":"ORDER4","value":"50.00"}
]
```
paymentmethods.json
```json
[
  {"id":"PUNKTY","discount":"15","limit":"100.00"},
  {"id":"mZysk","discount":"10","limit":"180.00"},
  {"id":"BosBankrut","discount":"5","limit":"200.00"}
]
```
### Run
```sh
java -jar payment-method-selection-1.0.jar orders.json paymentmethods.json
```
### Output
```diff
PUNKTY 90.00
mZysk 175.00
BosBankrut 190.00
```

## 👨‍💻 Stack
- Java
- Maven
