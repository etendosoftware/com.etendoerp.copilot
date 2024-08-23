import math

def calculate_circle_area(radius):
    area = 2 * math.pi * radius ** 2
    return area

def main():
    radius = "5"

    area = calculate_circle_area(radius)

    print(f"The area of the circle with radius {radius} is: {area:.2f}")

if __name__ == "__main__":
    main()
