#!/usr/bin/env python3
"""Draw every scene in all three sets.

  python3 -I tools/doodles-own/build.py <out-dir> [casual|semi|corporate] [name...]
      <out-dir>/<set>/<name>.svg                  the scene, viewable anywhere
      <out-dir>/<set>/kelvin_doodle_<name>.txt    the path list
  python3 -I tools/doodles-own/build.py --android android/kelvin/src/main/res/raw
      writes kelvin_doodle_<set>_<name>.txt, the files KelvinDoodle reads
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import playful, polished

# casual and semi share the animal scenes; semi draws them with a steady hand on a round backdrop
SETS = {
    "casual": [(m, "casual") for m in playful.SCENES],
    "semi": [(m, "semi") for m in playful.SCENES],
    "corporate": [(m, None) for m in polished.SCENES],
}

if __name__ == "__main__":
    if sys.argv[1] == "--android":
        raw = sys.argv[2]
        for set_name, scenes in SETS.items():
            for make, style in scenes:
                sc = make(style) if style else make()
                if style == "semi":
                    sc.backdrop()
                sc.fit(**getattr(sc, "fit_args", {}))
                with open(os.path.join(raw, f"kelvin_doodle_{set_name}_{sc.name}.txt"), "w") as fh:
                    fh.write(sc.txt())
        print("wrote", sum(len(v) for v in SETS.values()), "doodles to", raw)
        sys.exit(0)
    out, *rest = sys.argv[1:]
    only_set = rest[0] if rest and rest[0] in SETS else None
    names = rest[1:] if only_set else rest
    for set_name, scenes in SETS.items():
        if only_set and set_name != only_set:
            continue
        d = os.path.join(out, set_name)
        os.makedirs(d, exist_ok=True)
        for make, style in scenes:
            if names and make.__name__ not in names:
                continue
            sc = make(style) if style else make()
            if style == "semi":
                sc.backdrop()
            sc.fit(**getattr(sc, "fit_args", {}))
            with open(os.path.join(d, sc.name + ".svg"), "w") as fh:
                fh.write(sc.svg())
            with open(os.path.join(d, f"kelvin_doodle_{sc.name}.txt"), "w") as fh:
                fh.write(sc.txt())
            print(set_name, sc.name, len(sc.parts), "paths", round(len(sc.txt()) / 1024, 1), "KB")
