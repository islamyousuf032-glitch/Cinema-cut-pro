#ifndef EXPORT_CORE_H
#define EXPORT_CORE_H

#include <cstdint>
#include <vector>

struct ExportStats {
    int framesProcessed;
    double totalRenderTimeMs;
    int audioSamplesMixed;
};

class ExportCore {
public:
    ExportCore();
    ~ExportCore();

    ExportStats getStats() const;
    void resetStats();

private:
    ExportStats stats;
};

#endif // EXPORT_CORE_H
