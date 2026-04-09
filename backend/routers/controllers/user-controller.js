import config from '../../config/index.js';

async function getProfile(req, res) {
    try {
        const userId = req.user.user_id;

        const { data: user, error } = await config.supabase
            .from('users')
            .select('user_id, email, phone, first_name, last_name, birth_date, address, created_at')
            .eq('user_id', userId)
            .single();

        if (error || !user) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'USER_NOT_FOUND',
                    message: 'Utilizatorul nu a fost găsit'
                }
            });
        }

        return res.status(200).json({
            success: true,
            data: {
                user_id: user.user_id,
                email: user.email,
                phone: user.phone,
                first_name: user.first_name,
                last_name: user.last_name,
                birth_date: user.birth_date,
                address: user.address,
                created_at: user.created_at
            }
        });

    } catch (error) {
        console.error('Error getting profile:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea profilului'
            }
        });
    }
}

export default {
    getProfile
};
