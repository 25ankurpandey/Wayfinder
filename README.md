# README

## WayFinder: An Android Navigation & VR Companion App

**WayFinder** is a fully functional Android navigation application written in Kotlin that also serves as a companion app for Meta Quest VR devices. The app provides real-time location and navigation information, enabling an immersive mixed reality navigation experience.

## Features

- **Real-time Navigation**: Navigate on your Android device.
- **VR Companion**: Send location and navigation data to Meta Quest VR devices.  //VR app under development
- **Mixed Reality Integration**: Experience immersive navigation in a mixed reality environment.
- **Compass Integration**: Real-time azimuth updates using device sensors.
- **Route Conversion**: Convert geographic coordinates to Unity-compatible coordinates.
- **TCP Data Transfer**: Send navigation data to VR devices over TCP.
- **UDP Discovery**: Discover Meta Quest VR devices on the local network using UDP broadcasts.
- **Dynamic Path Updates**: Real-time updates to the navigation path based on the user's location.
- **User-friendly Interface**: Easy-to-use interface designed for seamless interaction.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [Features](#features)

## Installation

### Prerequisites

- Android Studio
- Unity3D
- Meta Quest VR device
- Kotlin 1.6+
- Meta Quest Developer Hub

### Steps

1. **Clone the Repository**:
   
bash
   git clone https://github.com/25ankurpandey/WayFinder.git
   cd WayFinder
   
2. **Open in Android Studio**:
   Open the cloned project in Android Studio.

3. **Set up Meta Quest**: (If developing for meta quest devices as well)
   - Ensure your Meta Quest VR device is connected and set up with the Meta Quest Developer Hub.
   - -Install Unity.
   - Install the necessary SDKs for Meta Quest integration.

4. **Build and Run**:
   - Build the project in Android Studio.
   - Run the application on your Android device.

## Usage

1. **Launch the App**:
   Open the WayFinder app on your Android device.

2. **Set Navigation**:
   - Enter your destination in the search bar.
   - Select preferred route.
   - Navigate in app or connect a Meta Quest VR to navigate in reatime.
    
3. **Pair with VR Device**:
   - Pair your Meta Quest VR device with the app.
   - Follow the on-screen instructions to complete the pairing process.

4. **VR Experience**:
   - Put on your Meta Quest VR headset.
   - Open the WayFinder companion app in the VR environment.  //App under development
   - Experience real-time navigation in a mixed reality setting.


### Components

- **Model**: Handles data and business logic.
- **View**: UI components and layouts.
- **ViewModel**: Manages UI-related data and handles communication between the Model and the View.
- **VR Integration**: Manages communication between the Android app and the Meta Quest VR device.
- **Compass Service**: Provides real-time azimuth updates using device sensors.
- **Directions Service**: Computes routes and direction-related functionalities.
- **Location Service**: Manages location updates and provides location data.
- **Route Converter**: Converts geographic coordinates to Unity-compatible coordinates.
- **TCP Client**: Sends navigation data to VR devices over TCP.
- **UDP Discovery**: Discovers Meta Quest VR devices on the local network using UDP broadcasts.
- **Navigation Fragment**: Manages the navigation interface and map interactions.
- **Map Fragment**: Manages map interactions and UI elements for route selection and navigation.

### Key Libraries

- **Navigation**: Android Google Map Frafment Navigation Component
- **VR Integration**: Meta Quest SDK
- **Asynchronous Processing**: Coroutines
- **UI Components**: Material Design
- **Google Maps**: Google Maps SDK for Android


### Contributing

- Fork the repository.
- Create a new branch for your feature or bug fix.
- Commit your changes and push your branch.
- Create a pull request detailing your changes.
