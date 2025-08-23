// native-brain/include/reservoir.h
#ifndef RESERVOIR_H
#define RESERVOIR_H

#include <vector>
#include <cstdint>
#include <string>

namespace brain {

struct Config {
    uint32_t neuron_count = 10000;
    uint32_t fanout = 10;
    float dt_ms = 20.0f;
};

class Reservoir {
public:
    Reservoir() = default;
    ~Reservoir();

    bool init(const Config &cfg, uint32_t seed = 1337);

    void apply_inputs(const std::vector<float>& input_vector);
    void step();
    std::string export_state_summary();
    std::vector<float> get_readout_vector();
    int create_word_cluster(const std::string &label, uint32_t cluster_size = 32);
    size_t neuron_count() const { return neurons_; }

private:
    uint32_t neurons_ = 0;
    uint32_t fanout_ = 0;
    float dt_ms_ = 20.0f;

    std::vector<float> potentials_;
    std::vector<uint8_t> spikes_;

    std::vector<uint32_t> out_index_ptr_;
    std::vector<uint32_t> out_targets_;
    std::vector<float> out_weights_;

    std::vector<float> readout_weights_;

    // Simple in-memory word-cluster registry (non-persistent; kept while process runs)
    std::vector<std::string> cluster_labels_;
    std::vector<uint32_t> cluster_sizes_;
};

} // namespace brain

#endif // RESERVOIR_H
