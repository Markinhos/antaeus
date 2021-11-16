## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build . -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!



# Candidate part

## Thought process
- The first step is to focus on the BillingService, ignoring the scheduling part.
- Once the basic implementation is done, it is needed to define how to deal with possible errors:
  - For NetworkException, the logical step is to retry for a few times and desist after it
  - For CurrencyMismatchException, since is an error that can be fixed by the customer, it could be sent an email and retry the next day
  - For CustomerNotFoundException, since there is no possible fix or customer to contact, it should be sent to a different table so another team can deal with those invoices.
- Next step is just to retrieve the invoices that we are interested instead of fetching all and filtering in memory.
- Add scheduling job. It runs every day, and checks if it is the first day of the month and if it is charge pending invoices. Otherwise, try to charge retryable invoices.

- From this point on the basic functionality is done. There are many improvements that it could be done:
  - Add a retry to every external service
  - Add more security patterns to external services (CircuitBreaker, RateLimiter, etc.)
  - Paginate calls to database
  - Set a retry counter per retryable invoice, and if reached send to customer operations.
  - Set a configurable option when to bill customers.
  - In a real production environment many of the external calls could be done in a more async way, making use of queues systems like SQS. This could be applied to email service for instance as no result is used and no sync call is needed.
  - It can be considered to move failed payments to a different table or other type of system like queues, as querying the whole database for a few failed invoices is very inefficient.

This took me about 3-4 h