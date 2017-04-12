# https://gist.github.com/sleekweasel/f4f0ef527f83a8aa74ac
# Indices for int[4] rectangles.

X0 ||= 0
Y0 ||= 1
X1 ||= 2
Y1 ||= 3

# Calculate clipped and obscured areas based on lists of orthogonal rectangles.
class Bounds
  def initialize(r)
    @rectangles = [r]
  end

  attr_reader :rectangles

  # Subtract this rectangle from this bounds' area.
  def subtract_rectangle(r)
    @rectangles = @rectangles.flat_map do |a|
      next [a] if disjoint?(a, r)
      p = []
      if a[X0] < r[X1] && r[X1] < a[X1]
        p << [r[X1], a[Y0], a[X1], a[Y1]]
        a[X1] = r[X1]
      end
      if a[X0] < r[X0] && r[X0] < a[X1]
        p << [a[X0], a[Y0], r[X0], a[Y1]]
        a[X0] = r[X0]
      end
      if a[Y0] < r[Y1] && r[Y1] < a[Y1]
        p << [a[X0], r[Y1], a[X1], a[Y1]]
        a[Y1] = r[Y1]
      end
      if a[Y0] < r[Y0] && r[Y0] < a[Y1]
        p << [a[X0], a[Y0], a[X1], r[Y0]]
      end
      next p
    end
  end

  # True if rectangles a and r do not overlap in any way
  def disjoint?(a, r)
    a[X0] >= r[X1] || a[X1] <= r[X0] || a[Y0] >= r[Y1] || a[Y1] <= r[Y0]
  end

  # Subtract the given bound's rectangles from this bound's area
  def subtract_bounds(b)
    b.rectangles.each { |r| subtract_rectangle(r) }
  end

  # Remove any of this bound that falls outside bound b.
  def clip_to_bounds(b)
    c = []
    @rectangles.each do |a|
      b.rectangles.each do |r|
        unless disjoint?(a, r)
          c << [[a[X0], r[X0]].max, [a[Y0], r[Y0]].max,
                [a[X1], r[X1]].min, [a[Y1], r[Y1]].min]
        end
      end
    end
    @rectangles = c
  end

  def leftmost_rectangle
    @rectangles.empty? ? nil : @rectangles.sort.first
  end

  def trivial?
    @rectangles.size == 1
  end

  def to_s
    super.to_s + @rectangles.to_s
  end
end
