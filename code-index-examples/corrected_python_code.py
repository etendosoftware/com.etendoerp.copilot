import math

def calculate_circle_area(radius):
    # Correct formula: π * r^2
    area = math.pi * radius ** 2
    return area

def main():
    # Correct type: radius should be a float
    radius = 5.0

    # Ensuring radius is a float before calculation
    area = calculate_circle_area(radius)

    # Correct string formatting
    print(f"The area of the circle with radius {radius} is: {area:.2f}")

if __name__ == "__main__":
    main()
