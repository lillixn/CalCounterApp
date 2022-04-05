Original App Design Project - README Template
===

# CalCounter

## Table of Contents
1. [Overview](#Overview)
1. [Product Spec](#Product-Spec)
1. [Wireframes](#Wireframes)
2. [Schema](#Schema)

## Overview
### Description
CalCounter is a streamlined, user-friendly solution to tracking calories. Traditional fitness apps have excessive features that over complicate and overstimulate. With CalCounter, users can quickly and effectively track their calories on the go. With location enabled services users can be reminded to enter calories at home, the workplace, the gym and more! For added convenience users can scan nutrition labels for quick calorie logging. Users will receive encouraging notifications to keep them motivated and on track with their calorie goals. Whether you’re a student athlete looking to up your calories or a busy mom on the go trying to watch your calorie intake, CalCounter makes it easy!

### App Evaluation
[Evaluation of your app across the following attributes]
- **Mobile:** The app is with users 24/7 so they can quickly enter in calories while on the go with ease and utilizes a camera and location enabled       services.
- **Story:**
- **Market:** 
     -Has the potential to help thousands of users.
     -This app doesn’t serve a niche group, instead it serves a wide variety of users. Those who are aiming to lose weight, maintain a healthy weight, or         increase their calories for bulking can all benefit from this app
     -Audience includes ages 15-80 yrs, all genders, races, identities, etc.
- **Habit:**
      -A user could use this app frequently, whether they’d like to track their calories every day or track specific goals(weeks, months, years, etc)
      -The user will keep coming back to receive motivational messages and see their progress in real time
- **Scope:**
      -This program will be moderately challenging, considering that we will be adding two mobile-oriented features to this calorie app. We believe that we         will face challenges with this app, but feel confident that we will be able to meet our goals and complete the app.

## Product Spec

### 1. User Stories (Required and Optional)

**Required Must-have Stories**

* User can create an account
* User can login
* User can set calorie goal
* User can log calories
* User can enable/disable location services
* User can enable/disable camera access
* User can scan label for calorie information
* User can edit calorie goals
* User can recieve notifications

**Optional Nice-to-have Stories**

* User can view progress from past calorie goals
* User can log additional health info (weight, BMI, etc.)

### 2. Screen Archetypes

* Login/ Create Account
   * User can login to keep track of calories and personal settings (location services, camera permissions, etc.)
   * User can enter calorie intake
   
* CalCounter home page
   * User can see a progress bar that is tracking their calorie goal
   * Enouraging messages appear here
* Camera
   * Can capture calorie information from packages and log it automatically
* Profile
   * User can set calorie goals, edit account information, and adjust privacy settings

### 3. Navigation

**Tab Navigation** (Tab to Screen)

* CalCounter Home
* Camera
* Profile

**Flow Navigation** (Screen to Screen)

<img src="https://github.com/lillixn/CodePathGroup2/blob/main/CalCounter%20Flowchart%20(1).png" width=600>


## Wireframes
[Sketch Wireframe]
<img src="https://user-images.githubusercontent.com/92342866/159396891-cc21afdf-e71c-432b-8134-d093a2c655b2.jpeg" width=400>


### [BONUS] Digital Wireframes & Mockups
[Home]
<img src="https://github.com/lillixn/CodePathGroup2/blob/main/CalCounterHome2.png" width=400>

[Scanner]
<img src="https://github.com/lillixn/CodePathGroup2/blob/main/CalCounterScan.png" width=400>

[Profile]
<img src="https://github.com/lillixn/CodePathGroup2/blob/main/CalCounterProfile.png" width=400>
### [BONUS] Interactive Prototype

## Schema 
### Models
#### Post

   | Property      | Type     | Description |
   | ------------- | -------- | ------------|
   | image         | File     | Scanning nutrition labels) |
   | objectId      | String   | Unique ID for users to track progress |
   | updatedAt     | DateTime | Date when calories entered or target hit |
   | createdAt     | DateTime | Starting dates when the user set new goal |
   
   | CRUD          | HTTP Verb     | Example |
   | ------------- | -------- | ------------|
   | Read          | GET      | Retrieving goal set by user |
   | Update        | PUT      | Updating goals |
   | Delete        | DELETE   | Deleting goals or calorie records |
   | Create        | POST     | New goal(s) |
   
### Networking
#### List of network requests by screen
   - Home Feed Screen
      - (Create/POST) Create new goal
      - (Delete) Delete goals/ calories
   - Capture Barcode Screen
      - (Create/POST) Create a new nutrition object
      - (Read/GET) Scan barcode
        ``` barcodeDetector.detect(imageEl)
          .then(barcodes => {
            barcodes.forEach(barcode => console.log(barcode.rawData));
           }
          .catch(err => {
            console.log(err);
           })
           ```
   - Profile Screen
      - (Read/GET) Query logged in user object
      - (Update/PUT) Update user profile image

