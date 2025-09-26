#!/usr/bin/env python3
"""
Unit conversion functions for data producers.
"""

# Temperature conversion
def celsius_to_fahrenheit(c: float) -> float:
    """Convert Celsius to Fahrenheit."""
    return (c * 9/5) + 32


def fahrenheit_to_celsius(f: float) -> float:
    """Convert Fahrenheit to Celsius."""
    return (f - 32) * 5/9


# Speed conversion
MPS_TO_MPH = 2.237


def mph_to_mps(mph: float) -> float:
    """Convert miles per hour to meters per second."""
    return mph / MPS_TO_MPH


def mps_to_mph(mps: float) -> float:
    """Convert meters per second to miles per hour."""
    return mps * MPS_TO_MPH


# Distance conversion
MILES_TO_KM = 1.60934


def km_to_miles(km: float) -> float:
    """Convert kilometers to miles."""
    return km / MILES_TO_KM


def miles_to_km(miles: float) -> float:
    """Convert miles to kilometers."""
    return miles * MILES_TO_KM